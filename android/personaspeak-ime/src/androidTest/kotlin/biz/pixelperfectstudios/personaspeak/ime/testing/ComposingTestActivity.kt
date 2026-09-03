package biz.pixelperfectstudios.personaspeak.ime.testing

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout

/** Hosts the real editor the ADR-0003 instrumentation drives. */
class ComposingTestActivity : Activity() {
    lateinit var editor: EditText
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editor = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val root = LinearLayout(this).apply { addView(editor) }
        setContentView(root)
        editor.requestFocus()
    }
}
