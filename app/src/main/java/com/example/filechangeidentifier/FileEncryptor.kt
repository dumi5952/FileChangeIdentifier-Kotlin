package com.example.filechangeidentifier

import java.io.File
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object FileEncryptor {
    private const val ALGORITHM = "AES"

    fun generateKey(): SecretKey {
        val decodedKey = Base64.getDecoder().decode(SECURITY_KEY)

        // Create the SecretKey using the decoded bytes
        return SecretKeySpec(decodedKey, "AES")
    }

    fun encryptFile(inputFile: File, outputFile: File, secretKey: SecretKey) {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val inputBytes = inputFile.readBytes()
        val outputBytes = cipher.doFinal(inputBytes)
        outputFile.writeBytes(outputBytes)
    }
}