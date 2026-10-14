# 一次性配置 Gitee 镜像更新通道：
#   1) 用你的 Gitee 令牌创建镜像仓库（默认 hongguotv-updates）
#   2) 把 GITEE_REPO（仓库变量）与 GITEE_TOKEN（密钥）写进 GitHub 仓库设置
#
# 用法（本机 PowerShell，需要已登录的 gh 与 Gitee 私人令牌）：
#   $env:GITEE_TOKEN = '<你的 Gitee 私人令牌>'
#   .\tools\setup-gitee-mirror.ps1
#
# Gitee 令牌获取步骤（只显示一次，生成后立刻复制）：
#   1. 登录 https://gitee.com （没账号先注册，需要手机号；建公开仓库可能还要实名，按提示完成）
#   2. 打开 https://gitee.com/profile/personal_access_tokens
#      （或：右上角头像 → 设置 → 左侧「安全设置」→「私人令牌」）
#   3. 点「生成新令牌」，描述填 hongguotv-updates-ci
#   4. 权限勾选：user_info（读账号名）+ projects（建仓库 / 传附件 / 提交 update.json）
#   5. 提交 → 输入 Gitee 登录密码确认 → 复制那串令牌（形如 32 位十六进制）
#   6. 用法：$env:GITEE_TOKEN = '<粘贴令牌>'  然后运行本脚本
# 想回收令牌：同一个页面点「删除」即可（删除后 CI 同步会失败，需重新生成并更新 GitHub 密钥）。
param(
    [string]$RepoName = 'hongguotv-updates',
    [string]$GitHubRepo = 'abai569/hongguoTV',
    [string]$Description = '橘影TV 更新镜像（update.json + APK）'
)
$ErrorActionPreference = 'Stop'
if (-not $env:GITEE_TOKEN) { throw '请先设置 $env:GITEE_TOKEN = "<Gitee 私人令牌>"（只勾 projects 权限即可）' }
$token = $env:GITEE_TOKEN

$user = Invoke-RestMethod "https://gitee.com/api/v5/user?access_token=$token"
$owner = $user.login
Write-Host "Gitee 账号：$owner"

$repoPath = "$owner/$RepoName"
try {
    Invoke-RestMethod -Method Post 'https://gitee.com/api/v5/user/repos' -Body @{
        access_token = $token
        name         = $RepoName
        description  = $Description
        private      = $false
        auto_init    = $true
    } | Out-Null
    Write-Host "已创建仓库：https://gitee.com/$repoPath"
} catch {
    Write-Host "创建仓库未成功（可能已存在，继续）：$($_.Exception.Message)"
}

gh variable set GITEE_REPO --repo $GitHubRepo --body $repoPath
$token | gh secret set GITEE_TOKEN --repo $GitHubRepo

Write-Host ''
Write-Host "完成：GitHub $GitHubRepo 已配置"
Write-Host "  GITEE_REPO  = $repoPath"
Write-Host "  GITEE_TOKEN = （已写入密钥，不再回显）"
Write-Host ''
Write-Host '下次发版时会自动把 release 与 update.json 同步到 Gitee；也可以让 AI 用令牌手动补同步已有版本。'
