# s3 probe v2: start dev server (cmd /c set JAVA_HOME ... && gradlew.bat runServer),
# redirect its output to a file, poll for 'Done (', then stop it through RCON.
# ASCII-only on purpose: powershell.exe reads .ps1 as ANSI when there is no BOM.
param(
    [Parameter(Mandatory = $true)][string]$LogName,
    [string]$JavaOpts = '',
    [int]$DoneTimeoutSec = 480,
    [switch]$ShowLog,
    [switch]$NoDaemon
)

$ErrorActionPreference = 'Continue'
$jdk = 'C:\Program Files\Java\jdk-21.0.11'

$root   = Split-Path $PSScriptRoot -Parent
$proj   = Join-Path $root 'cca-neoforge'
$outDir = Join-Path $PSScriptRoot 'out'
$outLog = Join-Path $outDir $LogName
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
if (Test-Path $outLog) { Remove-Item -LiteralPath $outLog -Force }

$env:JAVA_HOME = $jdk
if ($JavaOpts -ne '') {
    $env:JAVA_TOOL_OPTIONS = $JavaOpts
    Write-Host "JAVA_TOOL_OPTIONS=$JavaOpts"
} else {
    Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
}

$daemonArg = ''
if ($NoDaemon) { $daemonArg = ' --no-daemon' }
$argLine = "/c `"set `"JAVA_HOME=$jdk`" && gradlew.bat runServer --console=plain$daemonArg > `"$outLog`" 2>&1`""
$p = Start-Process -FilePath "$env:ComSpec" -ArgumentList $argLine -WorkingDirectory $proj -WindowStyle Hidden -PassThru
Write-Host "started pid=$($p.Id)"

$deadline = (Get-Date).AddSeconds($DoneTimeoutSec)
$done = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    if (Test-Path $outLog) {
        $hit = Select-String -LiteralPath $outLog -Pattern 'Done \(' -Quiet
        if ($hit) { $done = $true; break }
    }
    if ($p.HasExited) { break }
}
Write-Output "DONE_SEEN=$done"

if ($done) {
    Start-Sleep -Seconds 2
    python (Join-Path $PSScriptRoot 'rcon_probe.py') 127.0.0.1 25575 probe123 "stop" 2>&1 | Out-Null
    $waitUntil = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $waitUntil) {
        Start-Sleep -Seconds 2
        if (Select-String -LiteralPath $outLog -Pattern 'Stopping server' -Quiet) { break }
    }
}

# give gradle a moment to release the port, then make sure nothing is left
$waitUntil = (Get-Date).AddSeconds(90)
while ((Get-Date) -lt $waitUntil -and -not $p.HasExited) { Start-Sleep -Seconds 2 }
if (-not $p.HasExited) {
    try { & taskkill /PID $p.Id /T /F | Out-Null } catch { }
}

$port = (Test-NetConnection -ComputerName 127.0.0.1 -Port 25575 -WarningAction SilentlyContinue).TcpTestSucceeded
Write-Host "PROBE_DONE done=$done exited=$($p.HasExited) rcon_port_open=$port log=$outLog"

if ($ShowLog) {
    Write-Output '--- CCA-Mixin lines ---'
    Select-String -LiteralPath $outLog -Pattern 'CCA-Mixin|Mixin apply failed|TEST APPLIED' -ErrorAction SilentlyContinue |
        ForEach-Object { $_.Line.Trim() }
}
