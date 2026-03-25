package ru.mglife.mymax

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.mglife.mymax.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val messageList = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter

    private val crypto = CryptoManager()
    private lateinit var mls: MLSManager
    private lateinit var storage: StorageManager
    private lateinit var notifyHelper: NotificationHelper

    private var activeGroups = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация менеджеров
        mls = MLSManager(this)
        storage = StorageManager(this, mls, backupDepth = 2)
        notifyHelper = NotificationHelper(this)

        setupCrashHandler()
        loadState()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        // Настройка списка
        adapter = MessageAdapter(messageList)
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewMessages.adapter = adapter
        if (messageList.isNotEmpty()) {
            binding.recyclerViewMessages.scrollToPosition(messageList.size - 1)
        }

        // Обработка нажатия кнопки "Отправить"
        binding.buttonSend.setOnClickListener {
            val text = binding.editTextMessage.text.toString()
            if (text.isNotEmpty()) {
                sendMessage(text)
            }
        }

        // Имитация входящего сообщения по длинному нажатию
        binding.buttonSend.setOnLongClickListener {
            simulateIncomingEncryptedMessage()
            true
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("MAX_CRASH", "Emergency backup saving...")
                storage.save(ChatState(messageList, activeGroups.toList()), isEmergency = true)
            } catch (e: Exception) {
                android.util.Log.e("MAX_CRASH", "Failed to save emergency backup", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun loadState() {
        val savedState = storage.load()
        if (savedState != null) {
            messageList.clear()
            messageList.addAll(savedState.messages)
            activeGroups.clear()
            activeGroups.addAll(savedState.groups)
            
            activeGroups.forEach { groupId ->
                mls.startGroup(groupId)
            }
            android.util.Log.d("MAX_STORAGE", "State restored: ${messageList.size} messages, ${activeGroups.size} groups")
        } else {
            val defaultGroup = "chat_default"
            activeGroups.add(defaultGroup)
            mls.startGroup(defaultGroup)
        }
    }

    private fun persistState() {
        // Выносим сохранение в отдельный метод для удобства
        storage.save(ChatState(messageList.toList(), activeGroups.toList()))
    }

    override fun onPause() {
        super.onPause()
        persistState()
    }

    private fun sendMessage(rawText: String) {
        val targetGroupId = activeGroups.firstOrNull() ?: "chat_default"
        if (!activeGroups.contains(targetGroupId)) {
            activeGroups.add(targetGroupId)
            mls.startGroup(targetGroupId)
        }

        val encryptedB64 = mls.secureSend(rawText, targetGroupId)
        val decryptedText = mls.secureReceive(encryptedB64, targetGroupId)
        
        messageList.add(Message(decryptedText, true, isEncrypted = true))
        adapter.notifyItemInserted(messageList.size - 1)
        binding.recyclerViewMessages.scrollToPosition(messageList.size - 1)
        binding.editTextMessage.text.clear()

        // Сохраняем немедленно после добавления
        persistState()
    }

    private fun simulateIncomingEncryptedMessage() {
        val targetGroupId = activeGroups.firstOrNull() ?: "chat_default"
        val incomingB64 = "TUxTX1BBQ0tFVF9WMV9FUE9DSF8xX9Cy0L/QstCw0L/QstCw0L8="

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                delay(2000)
                val clearText = mls.secureReceive(incomingB64, targetGroupId)

                withContext(Dispatchers.Main) {
                    if (!clearText.startsWith("ERROR_")) {
                        notifyHelper.showNotification("Новое сообщение", clearText)
                        messageList.add(Message(clearText, false, isEncrypted = true))
                        adapter.notifyItemInserted(messageList.size - 1)
                        binding.recyclerViewMessages.scrollToPosition(messageList.size - 1)
                        
                        // Сохраняем после получения
                        persistState()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MAX_EXCEPTION", "Error: ${e.message}")
            }
        }
    }
}
