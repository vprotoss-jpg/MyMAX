package ru.mglife.mymax

data class Message(
    val text: String,
    val isSentByMe: Boolean,
    val isEncrypted: Boolean = true // По умолчанию помечаем как защищенное
)
