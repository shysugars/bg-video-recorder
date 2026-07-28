package com.example.bgvideo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** 词组列表适配器；中文释义根据 showZh 显示/隐藏。 */
class PhraseAdapter(
    private var items: List<Phrase>,
    private var showZh: Boolean
) : RecyclerView.Adapter<PhraseAdapter.VH>() {

    fun submit(list: List<Phrase>) {
        items = list
        notifyDataSetChanged()
    }

    fun setShowZh(show: Boolean) {
        if (showZh != show) {
            showZh = show
            notifyDataSetChanged()
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvEn: TextView = view.findViewById(R.id.tvEn)
        val tvZh: TextView = view.findViewById(R.id.tvZh)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_phrase, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.tvEn.text = p.en
        holder.tvZh.text = p.zh
        holder.tvZh.visibility = if (showZh) View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int = items.size
}
