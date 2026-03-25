package ru.mglife.mymax

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import ru.mglife.mymax.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val allChats = mutableListOf<Chat>()
    private val filteredChats = mutableListOf<Chat>()
    private lateinit var adapter: ChatAdapter
    private lateinit var mls: MLSManager
    private lateinit var storage: StorageManager
    private val crypto = CryptoManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mls = MLSManager(this)
        storage = StorageManager(this, mls, crypto)

        setupRecyclerView()
        setupFilters()
        setupBottomNavigation()
        loadChats()

        binding.fabAddChat.setOnClickListener {
            showAddChatDialog()
        }
        
        binding.buttonSearch.setOnClickListener {
            Toast.makeText(this, "Поиск пока не реализован", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(filteredChats) { chat ->
            openChat(chat)
        }
        binding.recyclerViewChats.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewChats.adapter = adapter
    }

    private fun setupFilters() {
        binding.tabLayoutFilters.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                applyFilter(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun applyFilter(position: Int) {
        filteredChats.clear()
        when (position) {
            0 -> filteredChats.addAll(allChats)
            1 -> filteredChats.addAll(allChats.filter { it.isGroup })
            2 -> filteredChats.addAll(allChats.filter { !it.isGroup })
        }
        adapter.notifyDataSetChanged()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chats -> true
                R.id.nav_contacts -> {
                    Toast.makeText(this, "Контакты (заглушка)", Toast.LENGTH_SHORT).show()
                    false
                }
                R.id.nav_settings -> {
                    // РЕАЛЬНЫЙ ПЕРЕХОД В НАСТРОЙКИ
                    startActivity(Intent(this, SettingsActivity::class.java))
                    false
                }
                R.id.nav_profile -> {
                    Toast.makeText(this, "Профиль (заглушка)", Toast.LENGTH_SHORT).show()
                    false
                }
                else -> false
            }
        }
    }

    private fun loadChats() {
        val state = storage.load()
        allChats.clear()
        if (state != null) {
            allChats.addAll(state.chats)
        }
        if (allChats.isEmpty()) {
            val defaultChat = Chat("chat_default", "Общий чат", true)
            allChats.add(defaultChat)
            storage.save(ChatState(allChats))
        }
        applyFilter(binding.tabLayoutFilters.selectedTabPosition)
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
        allChats.add(newChat)
        storage.save(ChatState(allChats))
        loadChats()
        openChat(newChat)
    }

    private fun openChat(chat: Chat) {
        val intent = Intent(this, MessageActivity::class.java)
        intent.putExtra("CHAT_ID", chat.id)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadChats()
    }
}
