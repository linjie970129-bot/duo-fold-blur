package com.duofold.blur

import android.graphics.Canvas
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder

/**
 * 动态壁纸：第三方 App 唯一无需 root、无需无障碍、能常驻生效的图层。
 * 覆盖桌面与锁屏；它不能接管其他 App 的画面（SystemUI 特权，任何第三方都拿不到）。
 */
class FoldBlurWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = DuoEngine()

    inner class DuoEngine : Engine() {

        companion object { private const val TAG = "FoldBlur" }

        private lateinit var source: FoldAngleSource
        private val renderer = DuoBlurRenderer()
        private val choreographer = Choreographer.getInstance()
        private var surfaceW = 0
        private var surfaceH = 0
        private var visible = false
        private var frameQueued = false

        private val frameCallback = Choreographer.FrameCallback { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            source = FoldAngleSource(this@FoldBlurWallpaperService).apply {
                onProgress = { requestFrame() }
                start()
            }
            Log.i(TAG, "hinge sensor available = ${source.hingeSensorAvailable}, mode = ${source.mode}")
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceW = width
            surfaceH = height
            source.onViewportChanged(width, height)
            requestFrame()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            super.onVisibilityChanged(isVisible)
            visible = isVisible
            if (visible) requestFrame() else {
                choreographer.removeFrameCallback(frameCallback)
                frameQueued = false
            }
        }

        override fun onDestroy() {
            choreographer.removeFrameCallback(frameCallback)
            if (::source.isInitialized) source.stop()
            super.onDestroy()
        }

        private fun requestFrame() {
            if (!visible || frameQueued) return
            frameQueued = true
            choreographer.postFrameCallback(frameCallback)
        }

        private fun drawFrame() {
            frameQueued = false
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                // RuntimeShader 依赖 GPU：必须用 lockHardwareCanvas()，lockCanvas() 是软件渲染，着色器会失效。
                canvas = holder.lockHardwareCanvas()
                if (canvas != null) {
                    renderer.draw(
                        canvas, surfaceW, surfaceH,
                        source.progress, resources.displayMetrics.density
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "render frame failed", t)
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (t: Throwable) {
                        Log.w(TAG, "unlock failed", t)
                    }
                }
            }
            // 只在开合过程中出帧，静止后自动停，避免常驻 60fps 耗电。
            if (source.isAnimating) requestFrame()
        }
    }
}
