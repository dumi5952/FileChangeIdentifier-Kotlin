package com.example.filechangeidentifier

import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object FileEncryptor {
    private const val ALGORITHM = "AES"

    fun generateKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(256) // AES-256 encryption
        return keyGen.generateKey()
    }

    fun encryptFile(inputFile: File, outputFile: File, secretKey: SecretKey) {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val inputBytes = inputFile.readBytes()
        val outputBytes = cipher.doFinal(inputBytes)
        outputFile.writeBytes(outputBytes)
    }
}