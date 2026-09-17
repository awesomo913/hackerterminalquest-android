package com.hackerquest.terminal.ui.terminal

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hackerquest.terminal.data.LineType
import com.hackerquest.terminal.data.TerminalLine
import com.hackerquest.terminal.databinding.ItemTerminalLineBinding

class TerminalAdapter : ListAdapter<TerminalLine, TerminalAdapter.LineViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LineViewHolder {
        val binding = ItemTerminalLineBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LineViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LineViewHolder(private val binding: ItemTerminalLineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(line: TerminalLine) {
            binding.tvLine.text = line.text
            binding.tvLine.setTextColor(colorFor(line.type))
        }

        private fun colorFor(type: LineType): Int = when (type) {
            LineType.INPUT   -> Color.parseColor("#00FF41")   // bright matrix green
            LineType.OUTPUT  -> Color.parseColor("#88CC88")   // muted green
            LineType.ERROR   -> Color.parseColor("#FF4444")   // red
            LineType.SUCCESS -> Color.parseColor("#00FF41")   // bright green
            LineType.STORY   -> Color.parseColor("#FFD700")   // gold
            LineType.SYSTEM  -> Color.parseColor("#00BFFF")   // cyan
            LineType.WARNING -> Color.parseColor("#FF8C00")   // orange
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TerminalLine>() {
            override fun areItemsTheSame(a: TerminalLine, b: TerminalLine) = a === b
            override fun areContentsTheSame(a: TerminalLine, b: TerminalLine) = a == b
        }
    }
}
