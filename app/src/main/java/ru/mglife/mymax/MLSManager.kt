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

    external fun decryptMessage(base64Data: String, groupId: String): String

    external fun createBackup(password: String, dataToBackup: String): String

    // --- Удобные обертки для использования в приложении ---

    /**
     * Инициализирует группу и сохраняет её состояние в защищенную папку приложения
     */
    fun startGroup(groupId: String): String {
        val path = context.filesDir.absolutePath
        return initGroupWithStorage(groupId, path)
    }

    /**
     * Шифрует сообщение для конкретной группы
     */
    fun secureSend(text: String, groupId: String): String {
        val path = context.filesDir.absolutePath
        return encryptMessage(text, groupId, path)
    }

    /**
     * Расшифровывает полученное Base64 сообщение
     */
    fun secureReceive(base64Data: String, groupId: String): String {
        return decryptMessage(base64Data, groupId)
    }

    /**
     * Создает зашифрованный бэкап данных
     */
    fun backupData(password: String, jsonContent: String): String {
        return createBackup(password, jsonContent)
    }
}
