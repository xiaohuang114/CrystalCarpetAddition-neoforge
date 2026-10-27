# serve.ps1 - start a dedicated dev server, wait until it prints "Done (", then RETURN WITHOUT STOPPING.
# Root is derived from this script's location ( <root>\_tools\serve.ps1 ), so no non-ASCII literal is needed.
# Usage:
#   .\_tools\serve.ps1 -LogName s5_run1.log -JavaOpts '-Dmixin.debug=true -Dmixin.debug.export=true'
param(
    [Parameter(Mandatory=$true)][string]$LogName,
    [string]$JavaOpts = "",
    [int]$TimeoutSec = 240
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$proj = Join-Path $root "cca-neoforge"
$outDir = Join-Path $root "_tools\out"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
$out = Join-Path $outDir $LogName
if (Test-Path $out) { Remove-Item $out -Force }

$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
if ($JavaOpts -ne "") { $env:JAVA_TOOL_OPTIONS = $JavaOpts }
else { Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue }

# Reset mixin export dir so stale classes from an earlier run cannot fake a pass.
$mixinOut = Join-Path $proj "run\.mixin.out"
if (Test-Path $mixinOut) { Remove-Item $mixinOut -Recurse -Force -ErrorAction SilentlyContinue }

$p = Start-Process -FilePath "cmd.exe" `
    -ArgumentList ('/c gradlew.bat runServer --console=plain > "' + $out + '" 2>&1') `
    -WorkingDirectory $proj -WindowStyle Hidden -PassThru

for ($i = 0; $i -lt $TimeoutSec; $i++) {
    Start-Sleep -Seconds 1
    if ((Test-Path $out) -and (Select-String -LiteralPath $out -Pattern 'Done \(' -Quiet)) {
        "SERVER READY (launcher pid=$($p.Id)) log=$out"
        exit 0
    }
    if ($p.HasExited -and -not (Test-Path $out)) {
        "LAUNCHER EXITED (pid=$($p.Id)) without a log file"
        exit 2
    }
}
"TIMEOUT after ${TimeoutSec}s - see $out"
exit 1
