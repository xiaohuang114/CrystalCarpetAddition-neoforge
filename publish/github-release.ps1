# 发布到 GitHub：建仓库 → push → 建 Release → 上传两个 jar
#
# 需要一个有 repo 权限的 Personal Access Token（classic 勾 repo，或 fine-grained 勾
# Contents: Read/Write + Administration: Read/Write）
#
# 示例：
#   $env:GITHUB_TOKEN = 'ghp_xxxxxxxx'
#   .\publish\github-release.ps1 -Owner YourName -Repo CCA-NeoForge -CreateRepo
#
#   若仓库已存在，去掉 -CreateRepo：
#   .\publish\github-release.ps1 -Owner YourName -Repo CCA-NeoForge
#
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Owner,
    [string]$Repo            = 'CrystalCarpetAddition-neoforge',
    [string]$Token           = $env:GITHUB_TOKEN,
    [string]$Tag             = 'v1.11.1-neoforge',
    [string]$ReleaseName     = 'Crystal Carpet Addition NeoForge · 1.11.1-neoforge',
    [string]$BodyFile        = 'docs/RELEASE-NOTES.md',
    [string]$Description     = 'Crystal Carpet Addition 的 NeoForge 移植 —— 13 条 Carpet 规则',
    [switch]$CreateRepo,
    [switch]$SkipPush,
    [switch]$Draft
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $Token) { throw "缺少 token：设置 `$env:GITHUB_TOKEN 或传 -Token" }

$H = @{
    Authorization          = "Bearer $Token"
    Accept                 = 'application/vnd.github+json'
    'X-GitHub-Api-Version' = '2022-11-28'
    'User-Agent'           = 'cca-publish-script'
}

function Invoke-GitHub {
    param([string]$Method, [string]$Uri, $Body, [string]$ContentType = 'application/json')
    $p = @{ Method = $Method; Uri = $Uri; Headers = $H }
    if ($Body) {
        if ($ContentType -eq 'application/json') {
            # PS 5.1 的 Invoke-RestMethod 收到 [string] 会按系统 ANSI(GBK) 编码发送，
            # 中文正文会变成非法 UTF-8，GitHub 回 400 Problems parsing JSON。
            # 必须自己转成 UTF-8 字节流。
            $json = $Body | ConvertTo-Json -Depth 10 -Compress
            $p.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
            $p.ContentType = 'application/json; charset=utf-8'
        } else {
            $p.Body = $Body
            $p.ContentType = $ContentType
        }
    }
    Invoke-RestMethod @p
}

# ── 1. 建仓库（可选）──────────────────────────────────────────
if ($CreateRepo) {
    try {
        $me = Invoke-GitHub GET 'https://api.github.com/user'
        "登录身份：$($me.login)"

        $payload = @{
            name        = $Repo
            description = $Description
            private     = $false
            has_issues  = $true
            has_wiki    = $false
            auto_init   = $false      # 不要 GitHub 自动建 initial commit，否则首次 push 会被拒
        }
        # 若 Owner 不是自己，走组织接口
        if ($me.login -ne $Owner) {
            $uri = "https://api.github.com/orgs/$Owner/repos"
        } else {
            $uri = 'https://api.github.com/user/repos'
        }
        $created = Invoke-GitHub POST $uri $payload
        "已创建仓库：$($created.html_url)"
    } catch {
        if ($_.Exception.Message -match 'already exists|422') {
            "仓库已存在，跳过创建：https://github.com/$Owner/$Repo"
        } else { throw }
    }
}

# ── 1.1 纠正仓库描述（旧脚本用 ANSI 发送，可能已写成乱码）────────
try {
    $fixed = Invoke-GitHub PATCH "https://api.github.com/repos/$Owner/$Repo" @{ description = $Description }
    "仓库描述：$($fixed.description)"
} catch {
    "仓库描述修正失败（不影响发布）：$($_.Exception.Message)"
}

# ── 1.5 git 调用封装 ───────────────────────────────────────
# Windows PowerShell 在 $ErrorActionPreference='Stop' 下会把原生程序的 stderr
# 当成终止错误（NativeCommandError），而 git 的推送进度正好走 stderr。
# 所以所有 git 调用统一走这里：临时降级错误策略 + 用 $script:GitExit 取 exit code。
$script:GitExit = 0
function Invoke-Git {
    param([string[]]$Arguments)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = & git @Arguments 2>&1
        $script:GitExit = $LASTEXITCODE
        $out | ForEach-Object { "$_" }
    } finally {
        $ErrorActionPreference = $prev
    }
}

# ── 2. git init / commit / push ──────────────────────────────
if (-not (Test-Path "$root\.git")) {
    Invoke-Git @('init', '-b', 'main') | Out-Null
    "git init 完成"
}

Invoke-Git @('config', 'user.name', $Owner) | Out-Null
Invoke-Git @('config', 'user.email', "$Owner@users.noreply.github.com") | Out-Null

Invoke-Git @('add', '-A') | Out-Null
$pending = @(Invoke-Git @('status', '--porcelain')).Count
if ($pending -gt 0) {
    Invoke-Git @('commit', '-m', 'release: CCA 1.11.1-neoforge') | Out-Null
    "已提交 $pending 个变更"
} else {
    "工作树干净，无需提交"
}

$remoteUrl = "https://github.com/$Owner/$Repo.git"
$existing = @(Invoke-Git @('remote'))
if ($existing -contains 'origin') {
    Invoke-Git @('remote', 'set-url', 'origin', $remoteUrl) | Out-Null
} else {
    Invoke-Git @('remote', 'add', 'origin', $remoteUrl) | Out-Null
}

$pushUrl = "https://x-access-token:$Token@github.com/$Owner/$Repo.git"
if (-not $SkipPush) {
    # 用 token 走 https，避免交互式登录
    Invoke-Git @('push', $pushUrl, 'main:main')   # 不用 -u：-u 会把带 token 的 URL 写进 .git/config
    if ($script:GitExit -ne 0) {
        # 常见原因：建仓库时 GitHub 自动生成了 initial commit（README / LICENSE），
        # 与本地历史无关，非 fast-forward 会被拒。先取远端再按 lease 覆盖。
        "push 被拒，先取远端历史再重试"
        Invoke-Git @('fetch', 'origin', 'main') | Out-Null
        Invoke-Git @('push', '-u', $pushUrl, 'main:main', '--force-with-lease')
        if ($script:GitExit -ne 0) {
            "lease 检查失败，改为强制覆盖远端自动生成的初始提交"
            Invoke-Git @('push', '-u', $pushUrl, 'main:main', '--force')
            if ($script:GitExit -ne 0) { throw "git push 失败（exit $script:GitExit）" }
        }
    }
    "已推送到 $remoteUrl"
}

# ── 2.5 把文档里的占位仓库 URL 换成真实地址，再补一次提交 ─────
$repoUrl = "https://github.com/$Owner/$Repo"
$docFiles = @('docs/MODRINTH.md', 'docs/RELEASE-NOTES.md', 'README.md', 'dist/README-安装说明.md')
$patched = 0
foreach ($rel in $docFiles) {
    $full = Join-Path $root $rel
    if (-not (Test-Path $full)) { continue }
    $text = [System.IO.File]::ReadAllText($full, [System.Text.Encoding]::UTF8)
    $new  = $text -replace '<https://github\.com/>', "<$repoUrl>" -replace 'https://github\.com/\)', "$repoUrl)"
    if ($new -ne $text) {
        [System.IO.File]::WriteAllText($full, $new, (New-Object System.Text.UTF8Encoding($false)))
        "已替换占位 URL：$rel"
        $patched++
    }
}
if ($patched -gt 0) {
    Invoke-Git @('add', '-A') | Out-Null
    $p2 = @(Invoke-Git @('status', '--porcelain')).Count
    if ($p2 -gt 0) {
        Invoke-Git @('commit', '-m', "docs: 填入真实仓库地址 $repoUrl") | Out-Null
        if (-not $SkipPush) {
            Invoke-Git @('push', $pushUrl, 'main:main')
            if ($script:GitExit -ne 0) { throw "git push（文档修订）失败（exit $script:GitExit）" }
        }
        "已提交文档修订（$p2 项）"
    }
}

# ── 3. 建 tag 并推送 ────────────────────────────────────────
$tags = @(Invoke-Git @('tag'))
if ($tags -notcontains $Tag) {
    Invoke-Git @('tag', '-a', $Tag, '-m', "CCA $Tag") | Out-Null
}
$pushUrl2 = "https://x-access-token:$Token@github.com/$Owner/$Repo.git"
Invoke-Git @('push', $pushUrl2, $Tag) | Out-Null

# ── 4. 建 Release ──────────────────────────────────────────
$bodyText = if (Test-Path "$root\$BodyFile") { Get-Content "$root\$BodyFile" -Raw -Encoding UTF8 } else { '' }

try {
    $rel = Invoke-GitHub POST "https://api.github.com/repos/$Owner/$Repo/releases" @{
        tag_name   = $Tag
        name       = $ReleaseName
        body       = $bodyText
        draft      = [bool]$Draft
        prerelease = $false
    }
    "已创建 Release：$($rel.html_url)"
} catch {
    if ($_.Exception.Message -match 'already_exists|422') {
        $rel = Invoke-GitHub GET "https://api.github.com/repos/$Owner/$Repo/releases/tags/$Tag"
        "Release 已存在，复用：$($rel.html_url)"
    } else { throw }
}

# ── 5. 上传 assets ─────────────────────────────────────────
$assets = @(
    'dist/CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar',
    'dist/CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge-sources.jar',
    'dist/sha1.txt',
    'dist/README-安装说明.md',
    'dist/RULE-TEST.md'
)

# 已有同名 asset 先删
$current = Invoke-GitHub GET "https://api.github.com/repos/$Owner/$Repo/releases/$($rel.id)/assets"
foreach ($a in $current) {
    Invoke-GitHub DELETE "https://api.github.com/repos/$Owner/$Repo/releases/assets/$($a.id)" | Out-Null
    "已删除旧 asset：$($a.name)"
}

foreach ($rel_path in $assets) {
    $full = Join-Path $root $rel_path
    if (-not (Test-Path $full)) { "跳过（不存在）：$rel_path"; continue }
    $name = Split-Path $full -Leaf
    # GitHub 的 ?name= 对非 ASCII 会丢字符（README-安装说明.md 变成 README-.md），统一转 ASCII
    $name = $name -replace '[^\x20-\x7E]', '-' -replace '-+', '-'
    $bytes = [System.IO.File]::ReadAllBytes($full)

    $uploadUri = "https://uploads.github.com/repos/$Owner/$Repo/releases/$($rel.id)/assets?name=$([uri]::EscapeDataString($name))"
    $r = Invoke-RestMethod -Method POST -Uri $uploadUri -Headers $H -Body $bytes `
            -ContentType 'application/octet-stream' -TimeoutSec 300
    "已上传：$name（$($r.size) B）"
}

""
"完成：$($rel.html_url)"
