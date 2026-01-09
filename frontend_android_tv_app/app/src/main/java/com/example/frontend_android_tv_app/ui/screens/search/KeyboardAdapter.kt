package com.example.frontend_android_tv_app.ui.screens.search

import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.frontend_android_tv_app.R
import com.example.frontend_android_tv_app.ui.Theme

class KeyboardAdapter(
    private val keys: List<KeyboardKey>,
    private val onKeyPressed: (KeyboardKey) -> Unit
) : RecyclerView.Adapter<KeyboardAdapter.KeyVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeyVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_keyboard_key, parent, false)
        return KeyVH(v)
    }

    override fun onBindViewHolder(holder: KeyVH, position: Int) {
        val key = keys[position]
        holder.bind(key)

        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            holder.applyFocused(hasFocus)
            if (hasFocus) {
                holder.itemView.animate().scaleX(1.06f).scaleY(1.06f).setDuration(100).start()
            } else {
                holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }
        }

        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                onKeyPressed(key)
                true
            } else {
                false
            }
        }

        holder.itemView.setOnClickListener {
            onKeyPressed(key)
        }
    }

    override fun getItemCount(): Int = keys.size

    class KeyVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.key_label)
        private val focusOverlay: View = itemView.findViewById(R.id.key_focus_overlay)

        fun bind(key: KeyboardKey) {
            label.text = when (key) {
                is KeyboardKey.CharKey -> key.c.toString()
                is KeyboardKey.TextKey -> key.label
            }
        }

        fun applyFocused(focused: Boolean) {
            if (focused) {
                val drawable = GradientDrawable().apply {
                    setColor(Theme.withAlpha(Theme.Primary, 0.12f))
                    setStroke(4, Theme.FocusRing)
                    cornerRadius = 14f
                }
                focusOverlay.background = drawable
                focusOverlay.alpha = 1f
            } else {
                focusOverlay.background = null
                focusOverlay.alpha = 0f
            }
        }
    }
}
