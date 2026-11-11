package com.duofold.blur

/**
 * AGSL (Android Graphics Shading Language) 片元着色器。
 * 复刻 iPhone Duo 开合转场的三件事：
 *   1. 外屏视口 -> 内屏全幅的连续缩放（穿透感）
 *   2. 自右向左推进的渐进高斯模糊带，强度在开合中段达到峰值（虚实转换）
 *   3. 铰链一侧的透视压缩，展开时回正
 *
 * 只需一张壁纸：合拢时取中心裁切当外屏视口，展开时露出完整内屏画面。
 * 这样只用一个 uniform shader 输入，避开多输入绑定在不同 Skia 版本上的兼容问题。
 */
object ShaderCode {

    val SRC = """
        uniform shader content;
        uniform float2 resolution;
        uniform float2 imageSize;
        uniform float progress;
        uniform float blurMax;
        uniform float hingeX;
        uniform float eye;
        uniform float coverScale;

        // 5x5 可分离高斯近似：权重 (1,4,6,4,1) 的外积，总和 256。
        // 降低性能开销可把 r 调小或把 25 次采样减成 3x3。
        half4 blur25(float2 c, float r) {
            float2 d = float2(r, r);
            half4 s = half4(0.0);
            s += content.eval(c + float2(-2.0*d.x, -2.0*d.y)) * 1.0;
            s += content.eval(c + float2(-1.0*d.x, -2.0*d.y)) * 4.0;
            s += content.eval(c + float2( 0.0*d.x, -2.0*d.y)) * 6.0;
            s += content.eval(c + float2( 1.0*d.x, -2.0*d.y)) * 4.0;
            s += content.eval(c + float2( 2.0*d.x, -2.0*d.y)) * 1.0;

            s += content.eval(c + float2(-2.0*d.x, -1.0*d.y)) * 4.0;
            s += content.eval(c + float2(-1.0*d.x, -1.0*d.y)) * 16.0;
            s += content.eval(c + float2( 0.0*d.x, -1.0*d.y)) * 24.0;
            s += content.eval(c + float2( 1.0*d.x, -1.0*d.y)) * 16.0;
            s += content.eval(c + float2( 2.0*d.x, -1.0*d.y)) * 4.0;

            s += content.eval(c + float2(-2.0*d.x, 0.0*d.y)) * 6.0;
            s += content.eval(c + float2(-1.0*d.x, 0.0*d.y)) * 24.0;
            s += content.eval(c + float2( 0.0*d.x, 0.0*d.y)) * 36.0;
            s += content.eval(c + float2( 1.0*d.x, 0.0*d.y)) * 24.0;
            s += content.eval(c + float2( 2.0*d.x, 0.0*d.y)) * 6.0;

            s += content.eval(c + float2(-2.0*d.x, 1.0*d.y)) * 4.0;
            s += content.eval(c + float2(-1.0*d.x, 1.0*d.y)) * 16.0;
            s += content.eval(c + float2( 0.0*d.x, 1.0*d.y)) * 24.0;
            s += content.eval(c + float2( 1.0*d.x, 1.0*d.y)) * 16.0;
            s += content.eval(c + float2( 2.0*d.x, 1.0*d.y)) * 4.0;

            s += content.eval(c + float2(-2.0*d.x, 2.0*d.y)) * 1.0;
            s += content.eval(c + float2(-1.0*d.x, 2.0*d.y)) * 4.0;
            s += content.eval(c + float2( 0.0*d.x, 2.0*d.y)) * 6.0;
            s += content.eval(c + float2( 1.0*d.x, 2.0*d.y)) * 4.0;
            s += content.eval(c + float2( 2.0*d.x, 2.0*d.y)) * 1.0;
            return s * (1.0 / 256.0);
        }

        half4 main(float2 coord) {
            float2 uv = coord / resolution;
            float p = clamp(progress, 0.0, 1.0);

            // 透视压缩：theta = p*90deg, z = sin(theta), S = eye/(eye+z)
            float z = sin(p * 1.5707963);
            float S = eye / (eye + z);
            float2 rel = uv - float2(hingeX, 0.5);
            rel.x *= mix(S, 1.0, p);
            float2 warped = rel + float2(hingeX, 0.5);

            // 视口插值：合拢=中心放大裁切，展平=完整全幅
            float scale = mix(coverScale, 1.0, p);
            float2 src = (warped - 0.5) * scale * imageSize + 0.5 * imageSize;

            // 渐进模糊：前沿自右向左推进；bell 让起止两帧保持锐利
            float front = mix(1.25, -0.25, p);
            float band = smoothstep(front - 0.32, front + 0.32, uv.x);
            float bell = sin(3.14159265 * p);
            float radius = blurMax * bell * band;

            half4 sharp = content.eval(src);
            half4 soft = blur25(src, radius);
            float amount = clamp(radius / max(blurMax * 0.55, 0.001), 0.0, 1.0);
            half4 color = mix(sharp, soft, amount);

            // 穿透式渐现：内屏完整画面从外屏虚化处透出
            half4 full = content.eval(uv * imageSize);
            float reveal = smoothstep(0.12, 0.96, p);
            color = mix(color, full, reveal * 0.88);

            return color;
        }
    """.trimIndent()
}
