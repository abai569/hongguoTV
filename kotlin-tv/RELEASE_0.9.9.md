# Kotlin 0.9.9：固定签名与备份改进

最低 Android 8.0 / API 26，versionCode 27。沿用固定发布签名，可直接覆盖安装并保留本机收藏、观看记录和设置。

## 签名

- 提交固定签名密钥 `kotlin-tv/signing/hongguotv.jks`，debug 与 release 均使用它签名。
- 此后各版本 APK 签名一致，可覆盖安装，软件内更新下载后可正常安装。
- 本版与电视上旧签名版本不同，首次仍需卸载一次；之后可覆盖安装。

## 备份

- 手机备份改用固定端口 8788，去掉随机 token，路径为 `/`、`/download`、`/import`。
- 下载文件名加时间戳：`hongguotv-library-<yyyyMMdd-HHmmss>.json`，并允许重复下载。
- 放宽同源来源校验。

## 稳定性

- 电视进入屏保或后台时不再关闭推送与备份服务（`onStop` 只收起弹窗，服务保留到用户关闭或应用销毁），修复 `ERR_CONNECTION_REFUSED`。

## 验证

源码默认版本为 `0.9.9` / `versionCode 27`。CI 执行核心测试、Android lint 和 Debug APK 构建，并在标签构建完成后上传 APK、`update.json` 和 `SHA256SUMS` 到 GitHub Release。
