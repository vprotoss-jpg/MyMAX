package ru.mglife.mymax

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    private var chats: List<Chat>,
    private val onChatClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageAvatar: ImageView = view.findViewById(R.id.imageAvatar)
        val textChatName: TextView = view.findViewById(R.id.textChatName)
        val textLastMessage: TextView = view.findViewById(R.id.textLastMessage)
        val textTime: TextView = view.findViewById(R.id.textTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]
        holder.textChatName.text = chat.name
        
        val lastMsg = chat.messages.lastOrNull()
        if (lastMsg != null) {
            val senderPrefix = if (chat.isGroup && lastMsg.senderName != null) {
                "${lastMsg.senderName}: "
            } else ""
            
            holder.textLastMessage.text = "$senderPrefix${lastMsg.text}"
            
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            holder.textTime.text = sdf.format(Date(chat.lastMessageTime))
        } else {
            holder.textLastMessage.text = "Нет сообщений"
            holder.textTime.text = ""
        }

        // Заглушка для аватара
        holder.imageAvatar.setImageResource(android.R.drawable.sym_def_app_icon)

        holder.itemView.setOnClickListener { onChatClick(chat) }
    }

    override fun getItemCount() = chats.size

    fun updateChats(newChats: List<Chat>) {
        this.chats = newChats
        notifyDataSetChanged()
    }
}
