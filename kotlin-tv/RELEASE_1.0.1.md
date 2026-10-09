# Kotlin 1.0.1：首页与搜索返回修复

最低 Android 8.0 / API 26，versionCode 29。沿用固定发布签名，可直接覆盖安装并保留本机收藏、观看记录和设置。

## 首页

- 修复「查看全部」按钮单独占一行时高度被异常拉伸、变成又窄又长竖条的问题。
- 卡片高度改为由内容自适应，不再随 HorizontalScrollView 拉伸。

## 搜索

- 修复从搜索结果页按返回键回到键盘页后，底部仍残留上一次搜索结果海报的问题。

## 验证

源码默认版本为 `1.0.1` / `versionCode 29`。CI 执行核心测试、Android lint 和 Debug APK 构建，并在标签构建完成后上传 APK、`update.json` 和 `SHA256SUMS` 到 GitHub Release。
