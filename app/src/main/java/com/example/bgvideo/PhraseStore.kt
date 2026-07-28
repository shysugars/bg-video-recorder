package com.example.bgvideo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 词组本地存储：SharedPreferences 中保存一个 JSON 数组。
 * 首次运行时写入默认词组。
 */
class PhraseStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("phrase_store", Context.MODE_PRIVATE)

    private val defaultPhrases = listOf(
        Phrase("break the ice", "打破僵局"),
        Phrase("a piece of cake", "小菜一碟"),
        Phrase("hit the books", "用功读书"),
        Phrase("once in a blue moon", "千载难逢"),
        Phrase("cost an arm and a leg", "极其昂贵"),
        Phrase("let the cat out of the bag", "无意中泄密"),
        Phrase("under the weather", "身体不适")
    )

    fun load(): List<Phrase> {
        val raw = prefs.getString(KEY, null)
        val list = mutableListOf<Phrase>()
        if (raw == null) {
            list.addAll(defaultPhrases)
            save(list)
        } else {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Phrase(o.getString("en"), o.getString("zh")))
            }
        }
        return list
    }

    fun add(phrase: Phrase) {
        val list = load().toMutableList()
        list.add(phrase)
        save(list)
    }

    fun removeAt(index: Int) {
        val list = load().toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            save(list)
        }
    }

    private fun save(list: List<Phrase>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("en", it.en)
                put("zh", it.zh)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "phrases_json"
    }
}
