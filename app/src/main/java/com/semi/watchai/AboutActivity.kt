package com.semi.watchai

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/** 关于页（空页面）。 */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<Button>(R.id.about_back).setOnClickListener {
            finish()
        }
    }
}
