package ru.mglife.mymax

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.*
import java.util.Base64
import javax.crypto.Cipher

class CryptoManager {

    private val KEY_ALIAS = "max_messenger_key"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    init {
        generateKeysIfNeeded()
    }

    // 1. Генерация пары ключей RSA (если их еще нет)
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

    // 2. Получение своего публичного ключа (его мы будем отправлять собеседнику)
    fun getMyPublicKey(): String {
        val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
        return Base64.getEncoder().encodeToString(publicKey.encoded)
    }

    // 3. Подпись сообщения своим закрытым ключом
    fun signMessage(data: ByteArray): String {
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(data)
        return Base64.getEncoder().encodeToString(signature.sign())
    }
}
