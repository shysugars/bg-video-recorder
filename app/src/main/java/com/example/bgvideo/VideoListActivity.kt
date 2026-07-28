package com.example.bgvideo

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 隐藏入口：录制视频列表。
 * 列出应用私有目录 Movies 下的 mp4 文件；单击播放，长按删除。
 */
class VideoListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var tvEmpty: TextView
    private var files: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_list)

        listView = findViewById(R.id.lvVideos)
        tvEmpty = findViewById(R.id.tvEmptyVideos)

        // 自定义 BaseAdapter：主行文件名，副行大小 + 录制时间；数据直接取自 files 字段
        listView.adapter = object : BaseAdapter() {
            override fun getCount(): Int = files.size
            override fun getItem(position: Int): Any = files[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: LayoutInflater.from(this@VideoListActivity)
                    .inflate(android.R.layout.simple_list_item_2, parent, false)
                val t1 = v.findViewById<TextView>(android.R.id.text1)
                val t2 = v.findViewById<TextView>(android.R.id.text2)
                val f = files[position]
                t1.text = f.name
                t2.text = fmtSubtitle(f)
                return v
            }
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val f = files[position]
            startActivity(
                Intent(this, VideoPlayerActivity::class.java).apply {
                    putExtra(VideoPlayerActivity.EXTRA_PATH, f.absolutePath)
                }
            )
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val f = files[position]
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete))
                .setMessage(f.name)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    if (f.delete()) {
                        loadFiles()
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
            true
        }

        loadFiles()
    }

    override fun onResume() {
        super.onResume()
        loadFiles()
    }

    private fun loadFiles() {
        val dir = File(getExternalFilesDir(null), "Movies")
        files = (dir.listFiles { _, name -> name.endsWith(".mp4", true) } ?: emptyArray())
            .sortedByDescending { it.lastModified() }
        (listView.adapter as? BaseAdapter)?.notifyDataSetChanged()
        tvEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        listView.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun fmtSubtitle(f: File): String {
        val size = Formatter.formatFileSize(this, f.length())
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(f.lastModified()))
        return "$size · $date"
    }
}
