package com.example.pdfviewer

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class SearchAdapter(
    private val onClick: (SearchResult) -> Unit
) : ListAdapter<SearchResult, SearchAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view as TextView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.text.text = "Strona ${item.page + 1}  ·  ${item.rects.size} trafień"
        holder.text.setOnClickListener { onClick(item) }
    }

    class ViewHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SearchResult>() {
            override fun areItemsTheSame(a: SearchResult, b: SearchResult) = a.page == b.page
            override fun areContentsTheSame(a: SearchResult, b: SearchResult) = a == b
        }
    }
}

class BookmarksAdapter(
    private val onJump: (Int) -> Unit,
    private val onRemove: (Int) -> Unit
) : ListAdapter<Int, BookmarksAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view as TextView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val page = getItem(position)
        holder.text.text = "Strona ${page + 1}"
        holder.text.setOnClickListener { onJump(page) }
        holder.text.setOnLongClickListener {
            onRemove(page)
            true
        }
    }

    class ViewHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Int>() {
            override fun areItemsTheSame(a: Int, b: Int) = a == b
            override fun areContentsTheSame(a: Int, b: Int) = a == b
        }
    }
}
