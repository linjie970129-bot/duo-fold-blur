package com.duofold.blur

import kotlin.math.PI
import kotlin.math.sin

/**
 * 把物理铰链角度映射成渲染参数。
 * 透视公式参照 Vyom-2007/DuoFoldWallpaper (MIT) 公开的思路：
 * z = d*sin(theta)，S = E/(E+z)，用 S 压缩铰链一侧的横向坐标。
 */
object FoldMath {

    fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

    fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = clamp01((x - a) / (b - a))
        return t * t * (3f - 2f * t)
    }

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** 0°=完全合拢，180°=完全展平；避开两端传感器死区。 */
    fun angleToProgress(degrees: Float): Float = smoothstep(8f, 168f, degrees)

    /** 展平过程中 z 由 sin 曲线变化，返回透视压缩系数 S。 */
    fun perspectiveScale(progress: Float, eye: Float = 3.2f): Float {
        val theta = clamp01(progress) * (PI.toFloat() / 2f)
        return eye / (eye + sin(theta))
    }
}
