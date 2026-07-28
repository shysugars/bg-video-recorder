package com.example.bgvideo

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

/** 添加词组的对话框：输入英文词组与中文释义。 */
class AddPhraseDialogFragment : DialogFragment() {

    interface OnAddListener {
        fun onPhraseAdded(phrase: Phrase)
    }

    private var listener: OnAddListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? OnAddListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val etEn = EditText(ctx).apply {
            hint = getString(R.string.hint_en)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        val etZh = EditText(ctx).apply {
            hint = getString(R.string.hint_zh)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        container.addView(etEn)
        container.addView(etZh)

        return AlertDialog.Builder(ctx)
            .setTitle(R.string.add_phrase)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val en = etEn.text.toString().trim()
                val zh = etZh.text.toString().trim()
                if (en.isNotEmpty() && zh.isNotEmpty()) {
                    listener?.onPhraseAdded(Phrase(en, zh))
                }
            }
            .create()
    }
}
