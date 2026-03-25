package ru.mglife.mymax

import android.content.Context

class MLSManager(private val context: Context) {
    companion object {
        init {
            System.loadLibrary("rust_lib")
        }
    }

    // --- Прямые JNI методы (соответствуют lib.rs) ---

    external fun getMlsVersion(): String
    
    external fun createGroup(groupId: String): String
    
    external fun initGroupWithStorage(groupId: String, storagePath: String): String

    external fun encryptMessage(message: String, groupId: String, storagePath: String): String

    // Добавлен параметр password для поддержки динамических ключей бэкапа
    external fun decryptMessage(base64Data: String, groupId: String, password: String): String

    external fun createBackup(password: String, dataToBackup: String): String

    // --- Удобные обертки ---

    fun startGroup(groupId: String): String {
        val path = context.filesDir.absolutePath
        return initGroupWithStorage(groupId, path)
    }

    fun secureSend(text: String, groupId: String): String {
        val path = context.filesDir.absolutePath
        return encryptMessage(text, groupId, path)
    }

    fun secureReceive(base64Data: String, groupId: String, backupPassword: String = ""): String {
        return decryptMessage(base64Data, groupId, backupPassword)
    }
}
