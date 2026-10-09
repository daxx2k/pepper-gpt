package com.softbankrobotics.pepper.pepperGPT

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class FullscreenEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INITIAL = "initial_text"
        const val EXTRA_RESULT = "result_text"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_editor)

        val edit = findViewById<EditText>(R.id.editFullscreen)
        val btnCancel = findViewById<Button>(R.id.btnCancel)
        val btnDone = findViewById<Button>(R.id.btnDone)

        edit.setText(intent.getStringExtra(EXTRA_INITIAL) ?: "")

        btnCancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        btnDone.setOnClickListener {
            val data = intent
            data.putExtra(EXTRA_RESULT, edit.text.toString())
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }
}
