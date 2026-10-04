package ru.warehouse.app.data

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String = "",
    val isFromUser: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)