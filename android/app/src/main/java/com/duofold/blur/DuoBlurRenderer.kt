package com.duofold.blur

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build

/** 把开合进度画成一张画面：API 33+ 走 AGSL，低版本走无着色器兜底。 */
class DuoBlurRenderer {

    private var wallpaper: Bitmap = buildDefaultWallpaper()
    private var inputShader: BitmapShader? = null
    private var runtimeShader: RuntimeShader? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun setWallpaper(bitmap: Bitmap) {
        wallpaper = bitmap
        inputShader = null
        runtimeShader = null
        paint.shader = null
    }

    fun draw(canvas: Canvas, width: Int, height: Int, progress: Float, density: Float) {
        if (width <= 0 || height <= 0) return
        ensureShader()
        val shader = runtimeShader
        if (shader == null) {
            drawFallback(canvas, width, height, progress)
            return
        }
        shader.setFloatUniform("resolution", width.toFloat(), height.toFloat())
        shader.setFloatUniform("imageSize", wallpaper.width.toFloat(), wallpaper.height.toFloat())
        shader.setFloatUniform("progress", progress)
        shader.setFloatUniform("blurMax", 22f * density)
        shader.setFloatUniform("hingeX", 0.5f)
        shader.setFloatUniform("eye", 3.2f)
        shader.setFloatUniform("coverScale", 0.62f)
        canvas.drawPaint(paint)
    }

    private fun ensureShader() {
        if (runtimeShader != null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        try {
            val input = BitmapShader(wallpaper, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val rs = RuntimeShader(ShaderCode.SRC)
            // AGSL 的 uniform shader 必须用 setInputShader 绑定，否则 eval() 返回黑。
            rs.setInputShader("content", input)
            inputShader = input
            runtimeShader = rs
            paint.shader = rs
        } catch (t: Throwable) {
            // 某些 Skia 版本编译 AGSL 会失败，静默降级而不是崩掉壁纸。
            runtimeShader = null
            paint.shader = null
        }
    }

    private fun drawFallback(canvas: Canvas, w: Int, h: Int, progress: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            Color.rgb(0x1B, 0x2A, 0x5E), Color.rgb(0xF2, 0xB2, 0x7A),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = Color.argb((90 * (1f - progress)).toInt(), 8, 10, 18)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    companion object {
        /** 程序生成的默认壁纸：有细线条和文字，模糊过渡才看得出来；不打包任何外部素材。 */
        fun buildDefaultWallpaper(): Bitmap {
            val bmp = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)

            p.shader = LinearGradient(
                0f, 0f, 1200f, 900f,
                intArrayOf(
                    Color.rgb(0x1B, 0x2A, 0x5E), Color.rgb(0x5B, 0x4A, 0x97),
                    Color.rgb(0xD2, 0x75, 0x6F), Color.rgb(0xF2, 0xB2, 0x7A)
                ),
                floatArrayOf(0f, 0.42f, 0.72f, 1f), Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, 1200f, 900f, p)
            p.shader = null

            p.shader = RadialGradient(
                880f, 250f, 430f,
                Color.argb(190, 255, 236, 190), Color.argb(0, 255, 236, 190),
                Shader.TileMode.CLAMP
            )
            c.drawRect(400f, 0f, 1200f, 700f, p)
            p.shader = null

            val hills = Path().apply {
                moveTo(0f, 690f); lineTo(230f, 470f); lineTo(400f, 610f)
                lineTo(650f, 390f); lineTo(900f, 640f); lineTo(1200f, 500f)
                lineTo(1200f, 900f); lineTo(0f, 900f); close()
            }
            p.color = Color.argb(160, 24, 26, 58)
            c.drawPath(hills, p)

            for (r in 0 until 4) {
                for (i in 0 until 7) {
                    val x = 95f + i * 150f
                    val y = 120f + r * 145f
                    p.color = Color.argb(30 + (r + i) % 3 * 12, 255, 255, 255)
                    c.drawRoundRect(x, y, x + 92f, y + 92f, 18f, 18f, p)
                    p.color = Color.argb(75, 255, 255, 255)
                    c.drawRect(x + 18f, y + 104f, x + 74f, y + 108f, p)
                }
            }

            p.color = Color.argb(235, 255, 255, 255)
            p.textSize = 74f
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            c.drawText("9:41", 80f, 86f, p)
            p.typeface = Typeface.DEFAULT

            p.color = Color.argb(42, 255, 255, 255)
            c.drawRoundRect(300f, 790f, 900f, 876f, 40f, 40f, p)
            p.color = Color.argb(110, 255, 255, 255)
            for (k in 0 until 4) c.drawCircle(390f + k * 140f, 833f, 26f, p)

            return bmp
        }
    }
}
