package ru.mglife.mymax

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import ru.mglife.mymax.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val chatList = mutableListOf<Chat>()
    private lateinit var adapter: ChatAdapter
    private lateinit var mls: MLSManager
    private lateinit var storage: StorageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mls = MLSManager(this)
        storage = StorageManager(this, mls)

        setupRecyclerView()
        loadChats()

        binding.fabAddChat.setOnClickListener {
            showAddChatDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(chatList) { chat ->
            openChat(chat)
        }
        binding.recyclerViewChats.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewChats.adapter = adapter
    }

    private fun loadChats() {
        val state = storage.load()
        chatList.clear()
        if (state != null) {
            chatList.addAll(state.chats)
        }
        // Если чатов совсем нет, добавим тестовый
        if (chatList.isEmpty()) {
            val defaultChat = Chat("chat_default", "Общий чат", true)
            chatList.add(defaultChat)
            storage.save(ChatState(chatList))
        }
        adapter.notifyDataSetChanged()
    }

    private fun showAddChatDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Добавить чат")
        
        val input = EditText(this)
        input.hint = "Имя контакта или группы"
        builder.setView(input)

        builder.setPositiveButton("Группа") { _, _ ->
            addChat(input.text.toString(), isGroup = true)
        }
        builder.setNeutralButton("Контакт (P2P)") { _, _ ->
            addChat(input.text.toString(), isGroup = false)
        }
        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

    private fun addChat(name: String, isGroup: Boolean) {
        if (name.isEmpty()) return
        val id = "chat_${System.currentTimeMillis()}"
        val newChat = Chat(id, name, isGroup)
        chatList.add(newChat)
        storage.save(ChatState(chatList))
        adapter.notifyItemInserted(chatList.size - 1)
        openChat(newChat)
    }

    private fun openChat(chat: Chat) {
        val intent = Intent(this, MessageActivity::class.java)
        intent.putExtra("CHAT_ID", chat.id)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadChats() // Обновляем список, чтобы видеть последние сообщения
    }
}
