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

class MainActivity : AppCompatActivity() {

    private val requestCode = 100
    private lateinit var fileObserver: FileObserver
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val fileChangeList = mutableListOf<String>()
    private val loggedFiles = mutableSetOf<String>() // To avoid duplicate logs


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ListView and Adapter
        listView = findViewById(R.id.listView)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, fileChangeList)
        listView.adapter = adapter

        // Set up button for backup action
        findViewById<Button>(R.id.button2).setOnClickListener {
            Thread {
                try {
                    val tempDir = File(cacheDir, "tempFiles")
                    if (!tempDir.exists()) tempDir.mkdirs()

                    val iterator = fileChangeList.iterator()
                    while (iterator.hasNext()) {
                        val filePath = iterator.next()
                        val actionType = filePath.split("-")[0]
                        val fileName = filePath.split(": ").last()
                        val originalFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                        if(actionType == "2" || actionType == "3"){
                            deleteFileFromWebDAV("${fileName}.enc",
                                // delete encripted File
                                 onSuccess = {
                                    // Remove the file from the list and update the ListView
                                    runOnUiThread {
                                        iterator.remove() // Remove the item from the iterator
                                        adapter.notifyDataSetChanged() // Notify adapter about changes
                                        Toast.makeText(this,"File removed!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                    onError = { error ->
                                        // Show error message
                                        runOnUiThread {
                                            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                        } else if (originalFile.exists()) {
                            val tempFile = File(tempDir, originalFile.name)
                            originalFile.copyTo(tempFile, overwrite = true)

                            // Encrypt the file
                            val encryptedFile = File(tempDir, "${originalFile.name}.enc")
                            val secretKey = FileEncryptor.generateKey() // Generate or retrieve the key
                            FileEncryptor.encryptFile(tempFile, encryptedFile, secretKey)

                            // Send the encrypted file to the WebDAV server
                            sendFileToWebDAV(encryptedFile, onSuccess = {
                                // Remove the file from the list and update the ListView
                                runOnUiThread {
                                    iterator.remove() // Remove the item from the iterator
                                    adapter.notifyDataSetChanged() // Notify adapter about changes
                                    Toast.makeText(this,"File Uploaded!", Toast.LENGTH_SHORT).show()
                                }
                            },
                                onError = { error ->
                                    // Show error message
                                    runOnUiThread {
                                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        } else {
                            iterator.remove() // Remove the item from the iterator
                        }
                    }

                    runOnUiThread {
                        Toast.makeText(this, "All files processed!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }

        // Check permissions and monitor file changes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                monitorFileChanges()
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
                    }
                }
            }
        }

        fileObserver.startWatching()
        sourceTextChange("Started monitoring: $pathToMonitor")
    }



    private fun logFileChange(message: String) {
        Log.d("FileObserver", message)
        runOnUiThread {
            fileChangeList.add(message)
            adapter.notifyDataSetChanged()
        }
    }

    private fun sourceTextChange(message: String) {
        // Reference the additional TextView
        val textViewSource = findViewById<TextView>(R.id.textViewSource)

        // Update the text value dynamically
        textViewSource.text = message
    }
    override fun onDestroy() {
        super.onDestroy()
        if (::fileObserver.isInitialized) {
            fileObserver.stopWatching()
        }
    }
}
