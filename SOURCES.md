# 参照来源与核实状态

## 【官方已核实】Android 开发者文档
- `RuntimeShader` / AGSL 自 **API 33** 引入：`https://developer.android.com/reference/android/graphics/RuntimeShader`
- `uniform shader` 输入用 `RuntimeShader.setInputShader(name, shader)` 绑定；`BitmapShader` 在 AGSL 中以**像素坐标** `eval()` 采样；`main()` 返回**预乘 alpha** 颜色
- `Sensor.TYPE_HINGE_ANGLE` 自 Android 11 起提供，官方说明其原始读数可用于「细粒度动画」
- 结论：`minSdk = 33`、`lockHardwareCanvas()`（软件 Canvas 跑不了 RuntimeShader）是本工程的两处硬约束

## 【已核实】GitHub 开源项目（README / 页面原文）
- **`Vyom-2007/DuoFoldWallpaper`（MIT）** —— 与需求几乎完全对口：`Sensor.TYPE_HINGE_ANGLE` + AGSL `RuntimeShader` 的 **Android 动态壁纸**，含 25-tap 渐进模糊、铰链透视压缩（`z=d·sinθ`、`S=E/(E+z)`）、按需 `Choreographer` 帧、内外双屏 Engine。本工程**参照其公开思路重写，未复制其代码**，并补了它没覆盖的两点：传感器不可用时的自动降级、不预置 vivo 机型几何参数。
- **`skydoves/Cloudy`（Apache-2.0）** —— `CloudyProgressive` 渐进式渐变模糊；API 33+ 用 AGSL，31–32 退化为均匀 `RenderEffect`，30- 退化为 CPU/scrim。用于确认渐进模糊的实现分层。
- **`andrew-malitchuk/axiom-sdk`（Apache-2.0）** —— 把 `TYPE_HINGE_ANGLE` 包成 Kotlin Flow；传感器缺失时 `getDefaultSensor()` 返回 null 并 fallback 到 180°。用于确认降级判据。

## 【仅二手来源，待实测】
- 上游 README 声称 Releases 提供预编译 APK；我打开其 Releases 页面失败（返回错误页），**未能核实是否真有 APK**，故不作为交付路径推荐。
- `axiom-sdk` README 关联的 `axiom-wallpaper-aos` 仓库地址返回 404（可能私有 / 改名 / 删除），不作为依据。
- Reddit `u/moomanjohnny` 在 Galaxy Z Fold 8 的复刻：AGSL + Presentation API + 内外屏两张截图交叉淡化；多家报道一致称需 SystemUI 特权 / root、斜视穿帮、无公开源码或 APK。**不要侧载网上搜到的「iPhone Duo 动画 APK」**。

## 【本机环境实测】
- `java` / `javac` / `gradle` / `adb` 均不存在，`ANDROID_HOME` 为空，`git clone github.com` 经代理 127.0.0.1:443 连接失败。
- 因此本工程**未在本机编译验证**；出包走 `.github/workflows/build-apk.yml`（云端装 JDK 17 + Android SDK 35 + Gradle 8.9）。
