package com.example.filechangeidentifier

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

object WebDAVUploader {

    fun checkServerReachability() {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://webdav.innovizion.tech")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("WebDAV", "Server unreachable: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("WebDAV", "Server reachable")
                } else {
                    Log.e("WebDAV", "Server not reachable: ${response.code}")
                }
            }
        })
    }

    fun sendFileToWebDAV(file: File, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val url = "$SERVER_URL/upload/${file.name}" // Replace "upload" if the path is different

        val client = OkHttpClient()

        // Use the new method to create MediaType
        val mediaType = "application/octet-stream".toMediaType()

        val requestBody = RequestBody.create(mediaType, file)

        val request = Request.Builder()
            .url(url)
            .put(requestBody)
            .header("Authorization", Credentials.basic(USERNAME, PASSWORD))
            .build()


        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("WebDAV", "Upload failed: ${e.message}")
                onError("Upload failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("WebDAV", "File uploaded successfully: ${file.name}")
                    onSuccess()
                } else {
                    Log.e("WebDAV", "Upload failed with status: ${response.code}")
                    onError("Upload failed with status: ${response.code}")
                }
            }
        })
    }

    fun deleteFileFromWebDAV(fileName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val url = "$SERVER_URL/upload/$fileName" // Make sure this URL matches your WebDAV structure

        val client = OkHttpClient()

        val request = Request.Builder()
            .url(url)
            .delete()
            .header("Authorization", Credentials.basic(USERNAME, PASSWORD))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("WebDAV", "Delete failed: ${e.message}")
                onError("Delete failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("WebDAV", "File deleted successfully: $fileName")
                    onSuccess()
                } else {
                    Log.e("WebDAV", "Delete failed with status: ${response.code}")
                    onError("Delete failed with status: ${response.code}")
                }
            }
        })
    }
}
