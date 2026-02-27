package ru.mglife.mymax

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import ru.mglife.mymax.databinding.ActivityMainBinding // Название зависит от вашего ID проекта

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val messageList = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter

    private val crypto = CryptoManager()
    private lateinit var notifyHelper: NotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        // Настройка списка
        adapter = MessageAdapter(messageList)
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewMessages.adapter = adapter

        // Обработка нажатия кнопки "Отправить"
        binding.buttonSend.setOnClickListener {
            val text = binding.editTextMessage.text.toString()
            if (text.isNotEmpty()) {
                sendMessage(text)
            }
        }
        val mls = MLSManager(this)
        val version = mls.getMlsVersion()
        val result = mls.startGroup("chat_123")
        android.util.Log.d("MLS_STORAGE", result)
        val groupStatus = mls.startGroup("my-secret-group-001")
        android.util.Log.d("MLS_RUST", version)
        android.util.Log.d("MLS_RUST", groupStatus)
        notifyHelper = NotificationHelper(this)

        // Имитируем входящее сообщение через 5 секунд
        binding.buttonSend.setOnLongClickListener {
            simulateIncomingEncryptedMessage()
            true
        }
    }

//    private fun sendMessage(rawText: String) {
//        // ТУТ БУДЕТ ВЫЗОВ ВАШЕГО КРИПТО-МЕНЕДЖЕРА
//        // Пока просто имитируем, что сообщение зашифровано
//        val newMessage = Message(text = rawText, isSentByMe = true)
//
//        messageList.add(newMessage)
//        adapter.notifyItemInserted(messageList.size - 1)
//        binding.recyclerViewMessages.scrollToPosition(messageList.size - 1)
//
//        // Очищаем поле ввода
//        binding.editTextMessage.text.clear()
//    }

//    private fun sendMessage(rawText: String) {
//        // Реальный процесс:
//        // 1. Берем сырой текст
//        val data = rawText.toByteArray()
//
//        // 2. Генерируем цифровую подпись (Sign)
//        val signature = crypto.signMessage(data)
//
//        // 3. Для отладки выведем часть подписи в лог
//        android.util.Log.d("CRYPTO", "Подпись: $signature")
//
//        // 4. Добавляем в список (визуально)
//        val newMessage = Message(
//            text = rawText,
//            isSentByMe = true,
//            isEncrypted = true
//        )
//
//        messageList.add(newMessage)
//        adapter.notifyItemInserted(messageList.size - 1)
//        binding.recyclerViewMessages.scrollToPosition(messageList.size - 1)
//        binding.editTextMessage.text.clear()
//    }

//    private fun sendMessage(rawText: String) {
//        val mls = MLSManager(this)
//
//        // 1. Шифруем через Rust/OpenMLS
//        val encryptedBase64 = mls.secureSend(rawText, "chat_123")
//
//        // 2. В логах теперь будет "забор" из Base64 символов
//        android.util.Log.d("MAX_SEND", "Отправляем на сервер: $encryptedBase64")
//
//        // 3. Добавляем в список (показываем оригинал для себя, но помечаем замком)
//        val newMessage = Message(text = rawText, isSentByMe = true, isEncrypted = true)
//        messageList.add(newMessage)
//        adapter.notifyItemInserted(messageList.size - 1)
//
//        binding.editTextMessage.text.clear()
//    }

    private fun sendMessage(rawText: String) {
        val mls = MLSManager(this)
        val groupId = "chat_123"

        // --- ПРОЦЕСС ОТПРАВКИ ---
        val encryptedB64 = mls.secureSend(rawText, groupId)
        android.util.Log.d("MAX_CRYPTO", "ЗАШИФРОВАНО: $encryptedB64")

        // --- ИМИТАЦИЯ ПРИЕМА (как будто получили от сервера) ---
        val decryptedText = mls.secureReceive(encryptedB64, groupId)
        android.util.Log.d("MAX_CRYPTO", "РАСШИФРОВАНО: $decryptedText")

        // Добавляем в список
        messageList.add(Message(decryptedText, true, true))
        adapter.notifyItemInserted(messageList.size - 1)
        binding.editTextMessage.text.clear()
    }

    private fun simulateIncomingEncryptedMessage() {
        val mls = MLSManager(this) // Создаем в главном потоке
        val groupId = "chat_123"

        // Проверьте, что в этой строке нет лишних пробелов в начале или конце!
        val incomingB64 = "TUxTX1BBQ0tFVF9WMV9FUE9DSF8xX9Cy0L/QstCw0L/QstCw0L8="

        Thread {
            try {
                Thread.sleep(5000)

                // Вызываем тяжелую операцию расшифровки в фоне
                val clearText = mls.secureReceive(incomingB64, groupId)

                runOnUiThread {
                    if (clearText.startsWith("ERROR_")) {
                        android.util.Log.e("MAX_ERROR", "Ошибка расшифровки: $clearText")
                    } else {
                        notifyHelper.showNotification("Новое сообщение", clearText)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
