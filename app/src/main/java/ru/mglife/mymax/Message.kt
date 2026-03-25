package ru.mglife.mymax

data class Message(
    val text: String,
    val isSentByMe: Boolean,
    val isEncrypted: Boolean = true,
    val senderName: String? = null // Имя отправителя для групповых чатов
)
