package com.example.filechangeidentifier

import java.io.File
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object FileEncryptor {
    private const val ALGORITHM = "AES/ECB/PKCS5Padding" // ECB mode without IV
    private const val KEY_LENGTH = 16 // 16 bytes = 128-bit key

    fun generateKey(): SecretKey {
        // Decode the Base64-encoded key
        val decodedKey = Base64.getDecoder().decode(SECURITY_KEY)
        require(decodedKey.size == KEY_LENGTH) { "Key must be $KEY_LENGTH bytes for AES-128." }
        return SecretKeySpec(decodedKey, "AES")
    }

    fun encryptFile(inputFile: File, outputFile: File, secretKey: SecretKey) {
        try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            // Read input file, encrypt, and write to output file
            val inputBytes = inputFile.readBytes()
            val outputBytes = cipher.doFinal(inputBytes)
            outputFile.writeBytes(outputBytes)

            println("File encrypted successfully: ${outputFile.path}")
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Error occurred while encrypting the file: ${e.message}")
        }
    }
}
