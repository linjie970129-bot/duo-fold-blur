# 折叠渐进模糊 · DuoFoldBlur

## 一、立刻看效果（不用编译）
1. 打开 `index.html`（电脑或手机浏览器都行）
2. 拖「铰链角度」滑块，或点「自动循环演示」
3. 手机浏览器里可「添加到主屏幕」，当全屏应用用

## 二、装到 vivo X Fold3 Pro（两条路选一条）

### A. 云端出包（不用装 Android Studio，推荐）
1. 把**整个 `duo-fold-blur` 文件夹**作为一个仓库 push 到 GitHub
2. 打开仓库 → Actions → Build APK → Run workflow
3. 跑完点 Artifacts，下载 `duo-fold-blur-debug-apk`
4. 把 APK 传到手机安装（需允许「未知来源」）

### B. 本机出包
1. 装 Android Studio 与 JDK 17
2. File → Open → 选 `android/` 文件夹
3. Build → Build APK(s)
4. 产物在 `android/app/build/outputs/apk/debug/`

## 三、安装后（先做这一次最小验证）
1. 打开 App，看顶部状态行是「铰链传感器：已开放」还是「未开放」
2. 点「模拟一次开合」→ 出现渐进模糊，说明渲染链路通了
3. 点「设为动态壁纸」→ 应用到桌面和锁屏
4. 缓慢开合手机，看是否跟手

## 四、边界（一句话）
第三方 App 拿不到 SystemUI 特权，所以它只在**桌面 / 锁屏**生效，不能让微信等 App 在开合时也变模糊；这不是代码能绕过的。

## 五、未实测 / 待确认
1. `vivo X Fold3 Pro` 是否把 `TYPE_HINGE_ANGLE` 开放给第三方 App —— 装完看状态行才知道；未开放时自动走「布局变化 → 680ms 插值转场」降级
2. 本机没有 JDK / Android SDK / Gradle / adb，也未连上 GitHub，所以**这里没能编译出 .apk**，工程为源码交付
3. 工程最低 API 33（AGSL RuntimeShader 的硬门槛），X Fold3 Pro 满足
