# 发布到 Modrinth：上传一个 version
#
# 前置：
#   1. 在 https://modrinth.com/dashboard/projects 建好项目（拿到 slug 或 project id）
#   2. 在 https://modrinth.com/settings/pats 建 Personal Access Token，勾选
#      「Create versions」+「Read projects」+（可选）「Write projects」
#
# 示例：
#   $env:MODRINTH_TOKEN = 'mrp_xxxxxxxx'
#   .\publish\modrinth-release.ps1 -Project 'cca-neoforge'
#
# 注意 mod 文件本身带 jar，改名成对外发布名再传：
#   脚本默认用 dist\CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar
#
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Project,
    [string]$Token        = $env:MODRINTH_TOKEN,
    [string]$Jar          = 'dist/CrystalCarpetAddition-neoforge-1.21.1-1.11.1-neoforge.jar',
    [string]$VersionNumber = '1.11.1-neoforge',
    [string]$VersionName   = 'CCA NeoForge 1.11.1 (MC 1.21.1)',
    [string]$ChangelogFile = 'docs/RELEASE-NOTES.md',
    [string[]]$GameVersions = @('1.21.1'),
    [string[]]$Loaders      = @('neoforge'),
    [string]$VersionType   = 'release',   # release | beta | alpha
    [string]$CarpetProjectId = '',        # 可选：填 Carpet: NeoForged 的 Modrinth project id，自动记为 required 依赖
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $Token) { throw "缺少 token：设置 `$env:MODRINTH_TOKEN 或传 -Token" }

$curl = (Get-Command curl.exe -ErrorAction SilentlyContinue).Source
if (-not $curl) { $curl = 'curl.exe' }

function Modrinth-Get([string]$Uri) {
    & $curl -sS -H "Authorization: $Token" -H 'User-Agent: cca-publish-script/1.0' $Uri
}

# ── 1. 解析项目 ───────────────────────────────────────────────
"查询项目：$Project"
$projJson = Modrinth-Get "https://api.modrinth.com/v2/project/$Project"
try { $proj = $projJson | ConvertFrom-Json } catch { throw "项目查询失败，返回：$projJson" }
if (-not $proj.id) { throw "项目不存在或 token 无权访问。返回：$projJson" }
"项目：$($proj.title)  id=$($proj.id)  slug=$($proj.slug)"

# ── 2. 组装 version JSON ─────────────────────────────────────
$changelog = if (Test-Path "$root\$ChangelogFile") { Get-Content "$root\$ChangelogFile" -Raw -Encoding UTF8 } else { '' }
$jarPath = Join-Path $root $Jar
if (-not (Test-Path $jarPath)) { throw "找不到 jar：$jarPath" }

$fileSize = (Get-Item $jarPath).Length
$sha1 = (Get-FileHash $jarPath -Algorithm SHA1).Hash.ToLower()
"jar：$Jar  ($fileSize B, sha1=$sha1)"

$deps = @()
if ($CarpetProjectId) {
    $deps += @{ project_id = $CarpetProjectId; dependency_type = 'required' }
    "记为 required 依赖：carpet @ $CarpetProjectId"
}

$versionObj = [ordered]@{
    name           = $VersionName
    version_number = $VersionNumber
    changelog      = $changelog
    dependencies   = $deps
    game_versions  = $GameVersions
    version_type   = $VersionType
    loaders        = $Loaders
    featured       = $true
    project_id     = $proj.id
    file_parts     = @('file')
    primary_file   = 'file'
}
$dataJson = ($versionObj | ConvertTo-Json -Depth 10 -Compress)

if ($DryRun) {
    "── DRY RUN ──"
    $dataJson
    return
}

# ── 3. 上传 ──────────────────────────────────────────────────
$dataFile = Join-Path $env:TEMP "modrinth-version-$([guid]::NewGuid()).json"
[System.IO.File]::WriteAllText($dataFile, $dataJson, (New-Object System.Text.UTF8Encoding($false)))

"上传中…"
$resp = & $curl -sS -X POST 'https://api.modrinth.com/v2/version' `
    -H "Authorization: $Token" `
    -H 'User-Agent: cca-publish-script/1.0' `
    -H 'Accept: application/json' `
    -F "data=<$dataFile;type=application/json" `
    -F "file=@$jarPath;type=application/java-archive"

Remove-Item $dataFile -Force -ErrorAction SilentlyContinue

try { $v = $resp | ConvertFrom-Json } catch { throw "上传失败，返回：$resp" }
if ($v.error) { throw "上传失败：$($v.error) / $($v.description)" }

""
"完成：https://modrinth.com/project/$($proj.slug)/version/$($v.id)"
"version id = $($v.id)"
"files      = $($v.files.name -join ', ')"
