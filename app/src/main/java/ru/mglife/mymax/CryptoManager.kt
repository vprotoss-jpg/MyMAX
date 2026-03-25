package ru.mglife.mymax

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.*
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class CryptoManager {

    private val KEY_ALIAS = "max_messenger_key"
    private val BACKUP_KEY_ALIAS = "max_backup_key"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    init {
        generateKeysIfNeeded()
        generateBackupKeyIfNeeded()
    }

    private fun generateKeysIfNeeded() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore"
            )

            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .build()

            keyPairGenerator.initialize(spec)
            keyPairGenerator.generateKeyPair()
        }
    }

    private fun generateBackupKeyIfNeeded() {
        if (!keyStore.containsAlias(BACKUP_KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )

            val spec = KeyGenParameterSpec.Builder(
                BACKUP_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    // Возвращает строковое представление ключа для использования в качестве пароля в Rust
    // На самом деле, лучше передавать хеш ключа или сам ключ в байтах
    fun getBackupPassword(): String {
        val key = keyStore.getKey(BACKUP_KEY_ALIAS, null) as SecretKey
        return Base64.getEncoder().encodeToString(key.encoded ?: "default_stable_pass".toByteArray())
    }
    
    // Позволяет принудительно перегенерировать ключ
    fun rotateBackupKey() {
        keyStore.deleteEntry(BACKUP_KEY_ALIAS)
        generateBackupKeyIfNeeded()
    }

    fun getMyPublicKey(): String {
        val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
        return Base64.getEncoder().encodeToString(publicKey.encoded)
    }

    fun signMessage(data: ByteArray): String {
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(data)
        return Base64.getEncoder().encodeToString(signature.sign())
    }
}
