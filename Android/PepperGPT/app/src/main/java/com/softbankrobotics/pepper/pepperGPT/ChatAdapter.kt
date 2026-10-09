package com.softbankrobotics.pepper.pepperGPT

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

class ChatAdapter(
    private val messages: List<ChatMessage>,
    private var currentTheme: String = "glass",
    private val onImageClick: ((String) -> Unit)? = null,
    private val onActivityClick: ((ChatMessage) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun updateTheme(theme: String) {
        this.currentTheme = theme
        notifyDataSetChanged()
    }

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_ROBOT = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].role == "user") TYPE_USER else TYPE_ROBOT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            val view = inflater.inflate(R.layout.item_message_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_message_robot, parent, false)
            RobotViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        if (holder is UserViewHolder) {
            holder.bind(msg, currentTheme)
        } else if (holder is RobotViewHolder) {
            holder.bind(msg, currentTheme, onImageClick, onActivityClick)
        }
    }

    override fun getItemCount(): Int = messages.size

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.text_message_body)
        fun bind(msg: ChatMessage, theme: String) {
            textView.text = msg.content

            val context = itemView.context
            when (theme) {
                "glass" -> {
                    textView.setTextColor(android.graphics.Color.parseColor("#2E3440"))
                    textView.background.setTint(androidx.core.content.ContextCompat.getColor(context, R.color.glass_accent))
                }
                "dark" -> {
                    textView.setTextColor(android.graphics.Color.BLACK)
                    textView.background.setTint(androidx.core.content.ContextCompat.getColor(context, R.color.dark_accent))
                }
                "soft" -> {
                    textView.setTextColor(android.graphics.Color.WHITE)
                    textView.background.setTint(androidx.core.content.ContextCompat.getColor(context, R.color.soft_accent))
                }
            }
        }
    }

    class RobotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.text_message_body)
        private val imageView: ImageView = itemView.findViewById(R.id.image_message)
        private val loadingOverlay: View = itemView.findViewById(R.id.image_loading)

        fun bind(msg: ChatMessage, theme: String, onImageClick: ((String) -> Unit)? = null, onActivityClick: ((ChatMessage) -> Unit)? = null) {
            val context = itemView.context
            ChatImageStyle.apply(imageView, msg.activityType)
            // Text content
            if (msg.content.isNotEmpty()) {
                textView.visibility = View.VISIBLE
                textView.text = msg.content
                
                // Allow clicking the text to re-open activity if it has metadata
                if (msg.activityType != null) {
                    textView.setOnClickListener { onActivityClick?.invoke(msg) }
                    // Add a visual cue or toast could go here, but let's keep it clean
                } else {
                    textView.setOnClickListener(null)
                }

                when (theme) {
                    "glass" -> {
                        textView.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.glass_text))
                        // Activity messages get a slightly different tint
                        if (msg.activityType != null) {
                            textView.background.setTint(android.graphics.Color.parseColor("#50000000")) // Darker translucent
                        } else {
                            textView.background.setTint(androidx.core.content.ContextCompat.getColor(context, R.color.glass_card))
                        }
                    }
                    "dark" -> {
                        textView.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.dark_text))
                        if (msg.activityType != null) {
                            textView.background.setTint(android.graphics.Color.parseColor("#333333"))
                        } else {
                            textView.background.setTint(androidx.core.content.ContextCompat.getColor(context, R.color.dark_card))
                        }
                    }
                    "soft" -> {
                        textView.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.soft_text))
                        if (msg.activityType != null) {
                            textView.background.setTint(android.graphics.Color.parseColor("#E0E6ED"))
                        } else {
                            textView.background.setTint(androidx.core.content.ContextCompat.getColor(context, R.color.soft_card))
                        }
                    }
                }
            } else {
                textView.visibility = View.GONE
            }

            // Image content
            if (!msg.imageUrl.isNullOrEmpty()) {
                imageView.visibility = View.VISIBLE
                loadingOverlay.visibility = View.VISIBLE
                
                imageView.setOnClickListener { onImageClick?.invoke(msg.imageUrl) }
                
                val request = when {
                    msg.imageUrl.startsWith("file://") ->
                        Picasso.get().load(java.io.File(msg.imageUrl.removePrefix("file://")))
                    msg.imageUrl.startsWith("/") ->
                        Picasso.get().load(java.io.File(msg.imageUrl))
                    else ->
                        Picasso.get().load(msg.imageUrl)
                }
                request
                    .error(R.drawable.ic_book_placeholder)
                    .into(imageView, object : com.squareup.picasso.Callback {
                        override fun onSuccess() {
                            loadingOverlay.visibility = View.GONE
                        }
                        override fun onError(e: Exception?) {
                            loadingOverlay.visibility = View.GONE
                        }
                    })
            } else {
                imageView.visibility = View.GONE
                loadingOverlay.visibility = View.GONE
            }
        }
    }
}
