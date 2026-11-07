package com.semi.watchai

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.semi.watchai.ui.home.LeftPageFragment
import com.semi.watchai.ui.settings.OthersActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 默认显示首页 Fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, LeftPageFragment())
                .commit()
        }

        // 其他按钮：跳转到设置中转页
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, OthersActivity::class.java))
        }
    }
}
