# Kotlin 0.9.8：搜索结果页返回搜索

最低 Android 8.0 / API 26，versionCode 26。沿用 0.9.7 发布签名，可直接覆盖安装并保留本机收藏、观看记录和设置。

## 搜索页

- 搜索结果页去掉“修改关键词”按钮。
- 按返回键直接从结果页回到搜索键盘页，输入框保留原关键词，可重新搜索。
- 标题栏提示“按返回键重新搜索”。

## 验证

源码默认版本为 `0.9.8` / `versionCode 26`。CI 执行核心测试、Android lint 和 Debug APK 构建，并在标签构建完成后上传 APK、`update.json` 和 `SHA256SUMS` 到 GitHub Release。
