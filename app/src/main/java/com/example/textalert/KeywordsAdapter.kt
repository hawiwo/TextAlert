package com.example.textalert

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class KeywordsAdapter(data: List<String>) : RecyclerView.Adapter<KeywordsAdapter.VH>() {
    val current = mutableListOf<String>()
    init { current.addAll(data) }
    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = LayoutInflater.from(parent.context).inflate(R.layout.item_keyword, parent, false) as TextView
        return VH(tv)
    }
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = current[position]
    }
    override fun getItemCount() = current.size
    fun setData(newData: List<String>) {
        current.clear()
        current.addAll(newData)
        notifyDataSetChanged()
    }
}

