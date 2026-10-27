# runclient.ps1 - start the dev CLIENT, wait until the main menu is provably up, then KILL ONLY THIS RUN'S client java.
# Root is derived from this script's location ( <root>\_tools\runclient.ps1 ), so no non-ASCII literal is needed.
# Why a separate script from serve.ps1: client and server SHARE cca-neoforge\run\ (logs, .mixin.out, config, mods, world),
# so the client run must (a) back up the server's latest.log before it gets overwritten and
# (b) copy the client log out immediately after the run (勘误 N10 / D-S6-4 same class of problem).
# Usage:
#   .\_tools\runclient.ps1 -LogName s9_client1.log -JavaOpts '-Dmixin.debug.export=true'
param(
    [Parameter(Mandatory=$true)][string]$LogName,
    [string]$JavaOpts = "",
    [int]$TimeoutSec = 600
)
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root "_tools\out"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
$out = Join-Path $outDir $LogName
$proj = Join-Path $root "cca-neoforge"
$run  = Join-Path $proj "run"

if (Test-Path $out) { Remove-Item $out -Force }

# (a) client/server share run\ : keep the server's latest.log before the client overwrites it.
if (Test-Path "$run\logs\latest.log") {
    Copy-Item "$run\logs\latest.log" (Join-Path $outDir "prev_server_latest.log") -Force
}
# Reset the mixin export dir so stale classes from an earlier run cannot fake a pass.
if (Test-Path "$run\.mixin.out") { Remove-Item "$run\.mixin.out" -Recurse -Force -ErrorAction SilentlyContinue }

$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
if ($JavaOpts -ne "") { $env:JAVA_TOOL_OPTIONS = $JavaOpts }
else { Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue }

# Snapshot PIDs so we can kill ONLY the client java this run starts (never the Gradle daemon).
$before = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty ProcessId)

Start-Process -FilePath "cmd.exe" `
    -ArgumentList ('/c gradlew.bat runClient --offline --console=plain > "' + $out + '" 2>&1') `
    -WorkingDirectory $proj -WindowStyle Hidden | Out-Null

# "Main menu is up" is provable from any of these lines: they are all printed before the title screen renders.
$deadline = (Get-Date).AddSeconds($TimeoutSec)
$verdict  = 'TIMEOUT'
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 3
    if (Test-Path $out) {
        $hit = Select-String -LiteralPath $out -Pattern 'Sound engine started|Narrator library|OpenAL initialized|Created: \d+x\d+' -Quiet
        if ($hit) { $verdict = 'READY'; break }
        if (Select-String -LiteralPath $out -Pattern 'BUILD FAILED|A fatal error has been detected|Exception in thread "main"' -Quiet) { $verdict = 'FAILED'; break }
    }
}

# Kill this run's client java only. GradleDaemon must survive (serve.ps1 relaunches through it).
Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue | Where-Object {
    $_.ProcessId -notin $before -and $_.CommandLine -notmatch 'GradleDaemon'
} | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

# (b) copy the client logs out NOW - a later server run reuses run\logs and would overwrite them.
if (Test-Path "$run\logs\latest.log") { Copy-Item "$run\logs\latest.log" (Join-Path $outDir ($LogName + '.latest.log')) -Force }
if (Test-Path "$run\logs\debug.log")  { Copy-Item "$run\logs\debug.log"  (Join-Path $outDir ($LogName + '.debug.log'))  -Force }

"CLIENT $verdict  log=$out"
if ($verdict -ne 'READY') { exit 1 }
