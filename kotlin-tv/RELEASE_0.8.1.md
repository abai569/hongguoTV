# Kotlin 0.8.1：修复搜索接口空响应

最低 Android 8.0 / API 26，versionCode 9。沿用 0.8.0 发布签名，可直接覆盖安装并保留本机收藏、观看记录和设置。

## 搜索修复

- App 搜索接口返回 HTTP 200 空响应时，短剧和漫剧搜索均回退到官网 SSR 搜索。
- 空响应会在 HTTP 边界被识别为显式错误，不再直接触发 `End of input at character 0`。
- 合法 JSON 但没有结果时保持为空，不会误触发官网回退。
- 增加漫剧搜索回退和空响应回归测试。

## 构建与验证

本版源码默认版本为 `0.8.1` / `versionCode 9`。CI 使用以下命令验证：

```sh
./kotlin-tv/gradlew -p kotlin-tv :core:test :app:lintDebug :app:assembleDebug --console=plain
```

本地签名发布包需要配置项目原有的签名凭据；未配置时使用 Debug APK 验证功能。

## 边界

官网搜索不提供可靠的短剧/漫剧类型标记，因此回退结果可能混入少量其他内容。App 搜索接口恢复后仍优先使用精确的类型结果。
