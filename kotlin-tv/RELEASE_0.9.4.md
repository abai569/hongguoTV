# Kotlin 0.9.4：更新通道与本仓库

最低 Android 8.0 / API 26，versionCode 22。沿用 0.9.3 发布签名，可直接覆盖安装并保留本机收藏、观看记录和设置。

## 更新通道

- 软件内更新的仓库由 `N3urda/hongguoTV-updates` 改为本仓库 `abai569/hongguoTV`。
- 发布流程自动生成并上传 `update.json` 与 `SHA256SUMS`，供软件内更新读取。
- 更新检查与 APK 下载加 `https://ghfast.top` 加速代理。
- “自动检查并下载更新”默认关闭，需要时在设置中开启。

## 其他

- 二维码白色静默区由 4 个模块缩到 2 个模块，白边更窄且可扫描。

## 验证

源码默认版本为 `0.9.4` / `versionCode 22`。CI 执行核心测试、Android lint 和 Debug APK 构建，并在标签构建完成后上传 APK、`update.json` 和 `SHA256SUMS` 到 GitHub Release。
