package ru.mglife.mymax

data class Chat(
    val id: String,
    val name: String,
    val isGroup: Boolean,
    val messages: MutableList<Message> = mutableListOf(),
    var lastMessageTime: Long = System.currentTimeMillis()
)
