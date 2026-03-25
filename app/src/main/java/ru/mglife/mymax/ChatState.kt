package ru.mglife.mymax

data class ChatState(
    val messages: List<Message> = emptyList(),
    val groups: List<String> = emptyList()
)
