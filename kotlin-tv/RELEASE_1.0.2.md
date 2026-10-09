# Kotlin 1.0.2：切换为 TV 上已安装版本的签名

最低 Android 8.0 / API 26，versionCode 30。

## 签名切换

- 构建签名从仓库内置的 hongguotv.jks 切换为你本机的 debug.keystore（通过 GitHub Secret 注入）。
- 这把密钥和你 TV 上已安装的 0.9.9 签名一致，可以**直接覆盖安装，不丢收藏和观看进度**。
- 之前 1.0.0 / 1.0.1 因为签名和 TV 上旧版不一致被系统拦截，此版本修复。

## 修复

- 首页「查看全部」卡片高度异常（1.0.1 已修）。
- 搜索结果返回后底部残留海报（1.0.1 已修）。

## 验证

CI 执行核心测试、Android lint 和 Debug APK 构建，并在标签构建完成后上传 APK、`update.json` 和 `SHA256SUMS` 到 GitHub Release。
