package com.duofold.blur

import android.animation.ValueAnimator
import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/**
 * 演示 / 调参页，同时兼作「最小验证」：
 * 顶部状态行会直接告诉你这台机有没有把铰链角度传感器开放给第三方 App。
 */
class DemoActivity : Activity() {

    private val renderer = DuoBlurRenderer()
    private lateinit var source: FoldAngleSource
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var seek: SeekBar
    private lateinit var loopBtn: Button
    private var loop: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        source = FoldAngleSource(this)
        // 传感器回调可能在界面构建完成前到达，这里做防护并把 start() 放到 onCreate 末尾。
        source.onProgress = {
            runOnUiThread {
                if (::preview.isInitialized && ::status.isInitialized) {
                    preview.invalidate()
                    refreshStatus()
                }
            }
        }

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = PreviewView(this)
        root.addView(
            preview,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(214, 14, 17, 24))
            setPadding(dp(16), dp(14), dp(16), dp(20))
        }

        status = TextView(this).apply {
            setTextColor(Color.rgb(0xCF, 0xD6, 0xE4))
            textSize = 12f
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        panel.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val sliderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val label = TextView(this).apply {
            text = "角度"
            setTextColor(Color.rgb(0x8B, 0x93, 0xA4))
            textSize = 12f
        }
        sliderRow.addView(label, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        seek = SeekBar(this).apply { max = 180; progress = 180 }
        sliderRow.addView(seek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(sliderRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        loopBtn = Button(this).apply { text = "自动循环演示" }
        val simBtn = Button(this).apply { text = "模拟一次开合" }
        row1.addView(loopBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row1.addView(simBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(row1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val applyBtn = Button(this).apply { text = "设为动态壁纸" }
        val autoBtn = Button(this).apply { text = "跟随实机" }
        row2.addView(applyBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(autoBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(row2, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val note = TextView(this).apply {
            text = "动态壁纸覆盖桌面/锁屏；受系统权限限制，无法接管其他 App 的开合画面。"
            setTextColor(Color.rgb(0x77, 0x80, 0x92))
            textSize = 11f
        }
        panel.addView(note, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        root.addView(panel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM })
        setContentView(root)

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                stopLoop()
                source.setManualDegrees(value.toFloat())
                preview.invalidate()
                refreshStatus()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })
        loopBtn.setOnClickListener { toggleLoop() }
        simBtn.setOnClickListener { stopLoop(); source.simulateFold(); preview.invalidate() }
        autoBtn.setOnClickListener {
            stopLoop()
            source.resumeAuto()
            refreshStatus()
            Toast.makeText(this, "已切回实机感应", Toast.LENGTH_SHORT).show()
        }
        applyBtn.setOnClickListener { applyLiveWallpaper() }

        // 所有 View 就绪后才注册传感器，避免回调早于界面初始化。
        source.start()
        refreshStatus()
        preview.invalidate()
    }

    private fun toggleLoop() {
        if (loop != null) {
            stopLoop()
            source.resumeAuto()
            refreshStatus()
            return
        }
        val anim = ValueAnimator.ofFloat(0f, 180f).apply {
            duration = 2600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
        }
        anim.addUpdateListener { v ->
            source.setManualDegrees(v.animatedValue as Float)
            seek.progress = source.degrees.toInt()
            preview.invalidate()
            refreshStatus()
        }
        anim.start()
        loop = anim
        loopBtn.text = "停止循环"
    }

    private fun stopLoop() {
        loop?.cancel()
        loop = null
        loopBtn.text = "自动循环演示"
    }

    private fun refreshStatus() {
        val line = if (source.hingeSensorAvailable) {
            "铰链传感器：已开放 · 逐帧跟手"
        } else {
            "铰链传感器：未开放 · 已启用布局变化降级"
        }
        status.text = "$line\n模式 ${source.mode} · 展开 ${(source.progress * 100).toInt()}% · 角度 ${source.degrees.toInt()}°"
    }

    private fun applyLiveWallpaper() {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, FoldBlurWallpaperService::class.java)
            )
            startActivity(intent)
        } catch (t: Throwable) {
            try {
                startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
            } catch (t2: Throwable) {
                Toast.makeText(this, "请在系统壁纸设置中选择「折叠渐进模糊」", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        loop?.cancel()
        source.stop()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class PreviewView(context: Context) : View(context) {
        override fun onDraw(canvas: Canvas) {
            renderer.draw(canvas, width, height, source.progress, resources.displayMetrics.density)
        }
    }
}
