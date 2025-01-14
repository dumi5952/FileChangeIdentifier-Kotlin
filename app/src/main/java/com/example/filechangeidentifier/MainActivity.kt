package com.example.filechangeidentifier

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.FileObserver
import android.provider.Settings
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.filechangeidentifier.WebDAVUploader.deleteFileFromWebDAV
import com.example.filechangeidentifier.WebDAVUploader.sendFileToWebDAV
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

class MainActivity : AppCompatActivity() {

    private val requestCode = 100
    private lateinit var fileObserver: FileObserver
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val fileChangeList = mutableListOf<String>()
    private lateinit var switchMonitoring: Switch
    private val logFilePath: File

    init {
        // Define the path for the "Logs" folder inside the Downloads directory
        val downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val logsFolder = File(downloadFolder, "Logs") // Create "Logs" folder inside Downloads

        // Check if the folder exists, if not, create it
        if (!logsFolder.exists()) {
            logsFolder.mkdirs() // Create the folder if it does not exist
        }

        // Define the log file inside the "Logs" folder
        logFilePath = File(logsFolder, "log_file.txt")

        // Check if the log file exists, if not, create it
        if (!logFilePath.exists()) {
            try {
                logFilePath.createNewFile() // Create the log file if it does not exist
            } catch (e: IOException) {
                e.printStackTrace() // Handle the error if file creation fails
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ListView and Adapter
        listView = findViewById(R.id.listView)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, fileChangeList)
        listView.adapter = adapter

        // Initialize Switch
        switchMonitoring = findViewById(R.id.switchMonitor)

        // Set Switch listener to enable/disable file monitoring
        switchMonitoring.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Enable file monitoring
                monitorFileChanges()
            } else {
                // Disable file monitoring
                stopMonitoring()
            }
        }

        // Check permissions and monitor file changes if enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
            } else {
                requestManageExternalStoragePermission()
            }
        } else {
            checkPermissions()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                monitorFileChanges()
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("Permission", "Permission not granted")
                Toast.makeText(this, "Permission denied. Please enable it in Settings.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), requestCode
            )
        } else {
            monitorFileChanges()
        }
    }

    private fun requestManageExternalStoragePermission() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = Uri.parse("package:${applicationContext.packageName}")
        startActivityForResult(intent, requestCode)
    }

    private fun monitorFileChanges() {
        val pathToMonitor =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath

        if (!File(pathToMonitor).exists()) {
            sourceTextChange("Directory does not exist: $pathToMonitor")
            return
        }

        // A map to store the last event timestamp for each file
        val eventTimestamps = mutableMapOf<String, Long>()
        val debounceTimeMs = 1000L // 1-second debounce period

        fileObserver = object : FileObserver(pathToMonitor, ALL_EVENTS) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null) {
                    val fullPath = "$pathToMonitor/$path"
                    val currentTime = System.currentTimeMillis()

                    // Check if the event for this file is within the debounce time
                    val lastEventTime = eventTimestamps[fullPath] ?: 0L
                    if (currentTime - lastEventTime < debounceTimeMs) {
                        return // Skip this event as it's too soon after the last one
                    }

                    val change = when (event) {
                        CREATE -> "1-File created: $path"
                        DELETE -> "2-File deleted: $path"
                        MODIFY -> "3-File modified: $path"
                        MOVED_TO -> "4-File moved to: $path"
                        else -> null
                    }

                    change?.let {
                        logFileChange(it)
                        eventTimestamps[fullPath] = currentTime // Update the event timestamp

                        // Perform file upload actions on create/modify
                        if (event == CREATE || event == MODIFY) {
                            val fileName = path.split("/").last() // Extract file name
                            val originalFile = File(pathToMonitor, path)

                            if (originalFile.exists()) {
                                // Process file: Encrypt and Upload
                                processAndUploadFile(originalFile)
                            }
                        }
                    }
                }
            }
        }

        fileObserver.startWatching()
        sourceTextChange("Started monitoring: $pathToMonitor")
    }

    private fun stopMonitoring() {
        if (::fileObserver.isInitialized) {
            fileObserver.stopWatching()
            sourceTextChange("Stopped monitoring")
        }
    }

    private fun processAndUploadFile(originalFile: File) {
        Thread {
            try {
                val tempDir = File(cacheDir, "tempFiles")
                if (!tempDir.exists()) tempDir.mkdirs()

                val tempFile = File(tempDir, originalFile.name)
                originalFile.copyTo(tempFile, overwrite = true)

                // Encrypt the file
                val encryptedFile = File(tempDir, "${originalFile.name}.enc")
                val secretKey = FileEncryptor.generateKey() // Generate or retrieve the key
                FileEncryptor.encryptFile(tempFile, encryptedFile, secretKey)

                // Send the encrypted file to the WebDAV server
                sendFileToWebDAV(encryptedFile, onSuccess = {
                    // File uploaded successfully, update list
                    runOnUiThread {
                        logFileChange("File uploaded: ${originalFile.name}")
                    }
                },
                    onError = { error ->
                        runOnUiThread {
                            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun logFileChange(message: String) {
        Log.d("FileObserver", message)
        runOnUiThread {
            fileChangeList.add(message)
            writeToLogFile(message)
            adapter.notifyDataSetChanged()
        }
    }

    private fun sourceTextChange(message: String) {
        // Reference the additional TextView
        val textViewSource = findViewById<TextView>(R.id.textViewSource)

        // Update the text value dynamically
        textViewSource.text = message
    }


    // Write log messages to the log file
    private fun writeToLogFile(message: String) {
        try {
            // Format the log message with timestamp
            val logMessage = "${System.currentTimeMillis()} - $message\n"

            // Open the log file in append mode (so that previous logs are preserved)
            val outputStream = FileOutputStream(logFilePath, true)
            val writer = OutputStreamWriter(outputStream)
            writer.append(logMessage)
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()  // Handle any errors while writing to the file
        }
    }

    // Manage log file size (optional)
    private fun manageLogFileSize() {
        val maxLogFileSize = 5 * 1024 * 1024  // 5 MB (adjust as needed)
        if (logFilePath.exists() && logFilePath.length() > maxLogFileSize) {
            // Archive or delete the old log file
            logFilePath.delete()
            // Optionally, create a new log file
            writeToLogFile("Log file exceeded max size, started new log.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::fileObserver.isInitialized) {
            fileObserver.stopWatching()
        }
    }
}
