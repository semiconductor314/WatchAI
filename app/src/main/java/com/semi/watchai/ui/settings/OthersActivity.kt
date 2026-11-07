package com.semi.watchai.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.Button
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.semi.watchai.R
import com.semi.watchai.ui.about.AboutActivity

/** "其他 >" 中转页：默认设置 / 模型库 / 系统提示词 / 关于。 */
class OthersActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_others)

        findViewById<Button>(R.id.others_back).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnDefaultSettings).setOnClickListener {
            startActivity(Intent(this, DefaultSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnModelLibrary).setOnClickListener {
            startActivity(Intent(this, ModelLibraryActivity::class.java))
        }

        findViewById<Button>(R.id.btnPromptSettings).setOnClickListener {
            startActivity(Intent(this, PromptSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        // 手表滚轮（rotary encoder）滚动按钮列表
        val scrollView = findViewById<ScrollView>(R.id.scrollOthers)
        scrollView.setOnGenericMotionListener { _, event ->
            if (event.action == MotionEvent.ACTION_SCROLL &&
                event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
            ) {
                val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL) * 50f
                scrollView.scrollBy(0, delta.toInt())
                true
            } else {
                false
            }
        }
        // 与首页历史记录一致：滚动容器必须持有焦点，滚轮事件才会派发到它
        scrollView.isFocusable = true
        scrollView.isFocusableInTouchMode = true
        scrollView.requestFocus()
        scrollView.post { scrollView.requestFocus() }
    }
}
