# Kotlin 0.9.7：更新下载多代理回退

最低 Android 8.0 / API 26，versionCode 25。沿用 0.9.6 发布签名，可直接覆盖安装并保留本机收藏、观看记录和设置。

## 更新下载

- 软件内更新的检查与下载改为多代理依次回退：
  1. `https://ghfast.top/`
  2. `https://git-proxy.abai.eu.org/`
  3. `https://gh-proxy.com/`
  4. 直连 `https://github.com/`
- 任一地址失败自动尝试下一个。
- 连接超时 10→6 秒，逐个失败更快。
- 全部失败时提示“无法连接更新服务器，请检查网络后重试”。

## 验证

源码默认版本为 `0.9.7` / `versionCode 25`。CI 执行核心测试、Android lint 和 Debug APK 构建，并在标签构建完成后上传 APK、`update.json` 和 `SHA256SUMS` 到 GitHub Release。
