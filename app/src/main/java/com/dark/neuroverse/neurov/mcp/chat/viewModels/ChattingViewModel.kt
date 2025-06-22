package com.dark.neuroverse.neurov.mcp.chat.viewModels

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.dark.neuroverse.neurov.mcp.chat.models.Message
import com.dark.neuroverse.neurov.mcp.chat.models.ROLE
import org.json.JSONArray
import org.json.JSONObject

class ChattingViewModel : ViewModel() {

    private val _messages = mutableStateListOf<Message>()
    val messages: List<Message> = _messages

    val streamingBuffer = mutableStateOf("")

    fun sendMessage(userInput: String) {
        streamingBuffer.value = "" // clear previous buffer

        val timeStamp = System.currentTimeMillis().toString()

        val userMessage = Message(ROLE.USER, userInput, timeStamp)
        _messages.add(userMessage)

        val messagesJson = JSONArray()
        val systemMessage = JSONObject()
        systemMessage.put("role", "system")
        systemMessage.put("content", "You are NeuroV AI assistant.")
        messagesJson.put(systemMessage)

        _messages.forEach { msg ->
            val msgJson = JSONObject()
            msgJson.put("role", msg.role.name.lowercase())
            msgJson.put("content", msg.content)
            messagesJson.put(msgJson)
        }

        val jsonPayload = JSONObject()
        jsonPayload.put("messages", messagesJson)
        jsonPayload.put("response_format", "text")
    }
}

