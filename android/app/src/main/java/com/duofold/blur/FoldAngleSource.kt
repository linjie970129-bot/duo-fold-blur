package com.duofold.blur

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Choreographer
import kotlin.math.abs

/**
 * 开合进度的数据源，三级降级（这是 vivo 等厂商是否开放铰链传感器的关键兜底）：
 *   1. HINGE_SENSOR   —— Sensor.TYPE_HINGE_ANGLE 可用，逐帧跟手（最理想）
 *   2. LAYOUT_FALLBACK —— 传感器未对第三方暴露，用内外屏宽高比跳变触发一次 680ms 插值转场
 *   3. MANUAL          —— 演示页滑块 / 自动循环 / 模拟开合
 */
class FoldAngleSource(context: Context) : SensorEventListener, Choreographer.FrameCallback {

    enum class Mode { HINGE_SENSOR, LAYOUT_FALLBACK, MANUAL }

    companion object {
        private const val SMOOTHING = 0.34f
        private const val FALLBACK_DURATION_MS = 680f
        private const val EXPANDED_RATIO = 0.90f
        private const val MOTION_HOLD_NS = 250_000_000L
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val hingeSensor =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) else null
    private val choreographer = Choreographer.getInstance()

    /** 最小验证就看这个值：true = 这台机把铰链角度开放给了第三方 App。 */
    val hingeSensorAvailable: Boolean get() = hingeSensor != null

    @Volatile var mode: Mode = Mode.MANUAL
        private set
    @Volatile var progress: Float = 1f
        private set
    @Volatile var degrees: Float = 180f
        private set

    var onProgress: ((Float) -> Unit)? = null

    private var registered = false
    private var filteredDegrees = 180f
    private var lastRatioExpanded: Boolean? = null
    private var animStartNs = -1L
    private var animFrom = 0f
    private var animTo = 1f
    private var lastChangeNs = 0L

    /** 静止后自动停帧，避免壁纸常驻 60fps 耗电。 */
    val isAnimating: Boolean
        get() = animStartNs >= 0L || System.nanoTime() - lastChangeNs < MOTION_HOLD_NS

    fun start() {
        mode = if (hingeSensor != null && sensorManager != null) {
            registered = sensorManager.registerListener(
                this, hingeSensor, SensorManager.SENSOR_DELAY_GAME
            )
            if (registered) Mode.HINGE_SENSOR else Mode.LAYOUT_FALLBACK
        } else {
            Mode.LAYOUT_FALLBACK
        }
    }

    fun stop() {
        if (registered) {
            sensorManager?.unregisterListener(this)
            registered = false
        }
        choreographer.removeFrameCallback(this)
    }

    /** 回到实机感应模式（演示滑块/自动循环结束后调用）。 */
    fun resumeAuto() {
        mode = if (registered) Mode.HINGE_SENSOR else Mode.LAYOUT_FALLBACK
        animStartNs = -1L
    }

    /** 手动指定角度，逐帧跟手，用于调参和录屏。 */
    fun setManualDegrees(value: Float) {
        mode = Mode.MANUAL
        animStartNs = -1L
        degrees = value.coerceIn(0f, 180f)
        filteredDegrees = degrees
        setProgress(FoldMath.angleToProgress(degrees))
    }

    /** 模拟一次开合：用于验证降级动画，也能让没有铰链传感器的机器看到效果。 */
    fun simulateFold() {
        resumeAuto()
        animFrom = if (progress > 0.5f) 1f else 0f
        animTo = 1f - animFrom
        animStartNs = System.nanoTime()
        choreographer.postFrameCallback(this)
    }

    /**
     * 由 WallpaperService.Engine.onSurfaceChanged 调用。
     * 外屏细长(宽高比约 0.4x)，内屏接近方形(约 1.1x)，跨越 0.90 即判定发生了一次开合。
     */
    fun onViewportChanged(width: Int, height: Int) {
        if (height <= 0 || mode == Mode.MANUAL) return
        val expanded = width.toFloat() / height.toFloat() > EXPANDED_RATIO
        val previous = lastRatioExpanded
        lastRatioExpanded = expanded
        if (previous != null && previous != expanded && mode == Mode.LAYOUT_FALLBACK) {
            animateTo(if (expanded) 1f else 0f)
        }
    }

    private fun animateTo(target: Float) {
        animFrom = progress
        animTo = target
        animStartNs = System.nanoTime()
        choreographer.removeFrameCallback(this)
        choreographer.postFrameCallback(this)
    }

    private fun setProgress(p: Float) {
        progress = FoldMath.clamp01(p)
        lastChangeNs = System.nanoTime()
        onProgress?.invoke(progress)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (mode == Mode.MANUAL || event.values.isEmpty()) return
        val raw = event.values[0]
        filteredDegrees += (raw - filteredDegrees) * SMOOTHING
        degrees = filteredDegrees
        val next = FoldMath.angleToProgress(filteredDegrees)
        if (abs(next - progress) > 0.0015f) setProgress(next)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun doFrame(frameTimeNanos: Long) {
        if (animStartNs < 0L || mode == Mode.MANUAL) return
        val elapsedMs = (frameTimeNanos - animStartNs) / 1_000_000f
        val k = FoldMath.clamp01(elapsedMs / FALLBACK_DURATION_MS)
        setProgress(FoldMath.lerp(animFrom, animTo, FoldMath.smoothstep(0f, 1f, k)))
        if (k < 1f) {
            choreographer.postFrameCallback(this)
        } else {
            animStartNs = -1L
        }
    }
}
