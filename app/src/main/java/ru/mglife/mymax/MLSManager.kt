package ru.mglife.mymax

//class MLSManager {
//    companion object {
//        init {
//            System.loadLibrary("rust_lib")
//        }
//    }
//
//    external fun getMlsVersion(): String
//
//    // Новая функция для создания группы
//    external fun createGroup(groupId: String): String
//}

class MLSManager(private val context: android.content.Context) {
    companion object {
        init {
            System.loadLibrary("rust_lib")
        }
    }

    external fun getMlsVersion(): String
    // Добавляем параметр storagePath
    external fun initGroupWithStorage(groupId: String, storagePath: String): String

    fun startGroup(groupId: String): String {
        // Получаем путь к защищенной папке приложения
        val path = context.filesDir.absolutePath
        return initGroupWithStorage(groupId, path)
    }

    external fun encryptMessage(message: String, groupId: String, storagePath: String): String

    fun secureSend(text: String, groupId: String): String {
        return encryptMessage(text, groupId, context.filesDir.absolutePath)
    }

    external fun decryptMessage(base64Data: String, groupId: String): String

    fun secureReceive(base64Data: String, groupId: String): String {
        return decryptMessage(base64Data, groupId)
    }
}
