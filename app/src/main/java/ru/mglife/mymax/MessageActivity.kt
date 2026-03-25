package ru.mglife.mymax

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.mglife.mymax.databinding.ActivityMessageBinding

class MessageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMessageBinding
    private lateinit var mls: MLSManager
    private lateinit var storage: StorageManager
    private lateinit var adapter: MessageAdapter
    
    private var currentChat: Chat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mls = MLSManager(this)
        storage = StorageManager(this, mls)

        val chatId = intent.getStringExtra("CHAT_ID")
        loadChat(chatId)
        setupRecyclerView()

        binding.buttonSend.setOnClickListener {
            val text = binding.editTextMessage.text.toString()
            if (text.isNotEmpty()) sendMessage(text)
        }

        binding.buttonAttach.setOnClickListener {
            Toast.makeText(this, "Функция вложений скоро появится", Toast.LENGTH_SHORT).show()
        }
        
        // Оставляем длинный клик для тестов
        binding.buttonSend.setOnLongClickListener {
            simulateIncomingMessage()
            true
        }
    }

    private fun loadChat(chatId: String?) {
        val state = storage.load()
        currentChat = state?.chats?.find { it.id == chatId }
        if (currentChat == null) {
            finish()
            return
        }
        supportActionBar?.title = currentChat?.name
        mls.startGroup(currentChat!!.id)
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(currentChat?.messages ?: mutableListOf())
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewMessages.adapter = adapter
        if (adapter.itemCount > 0) {
            binding.recyclerViewMessages.scrollToPosition(adapter.itemCount - 1)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_settings -> {
                Toast.makeText(this, "Настройки чата", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun sendMessage(text: String) {
        currentChat?.let { chat ->
            val encryptedB64 = mls.secureSend(text, chat.id)
            val decryptedText = mls.secureReceive(encryptedB64, chat.id)
            
            val message = Message(decryptedText, true, isEncrypted = true)
            chat.messages.add(message)
            chat.lastMessageTime = System.currentTimeMillis()
            
            adapter.notifyItemInserted(chat.messages.size - 1)
            binding.recyclerViewMessages.scrollToPosition(chat.messages.size - 1)
            binding.editTextMessage.text.clear()
            
            saveGlobalState()
        }
    }

    private fun simulateIncomingMessage() {
        currentChat?.let { chat ->
            val incomingB64 = "TUxTX1BBQ0tFVF9WMV9FUE9DSF8xX9Cy0L/QstCw0L/QstCw0L8="
            lifecycleScope.launch(Dispatchers.Default) {
                delay(2000)
                val clearText = mls.secureReceive(incomingB64, chat.id)
                withContext(Dispatchers.Main) {
                    if (!clearText.startsWith("ERROR_")) {
                        val message = Message(clearText, false, isEncrypted = true)
                        chat.messages.add(message)
                        adapter.notifyItemInserted(chat.messages.size - 1)
                        binding.recyclerViewMessages.scrollToPosition(chat.messages.size - 1)
                        saveGlobalState()
                    }
                }
            }
        }
    }

    private fun saveGlobalState() {
        val state = storage.load()
        state?.let {
            val updatedChats = it.chats.map { chat ->
                if (chat.id == currentChat?.id) currentChat!! else chat
            }
            storage.save(ChatState(updatedChats))
        }
    }
}
