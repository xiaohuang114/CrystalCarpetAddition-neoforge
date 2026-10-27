# Probe: start dev server, wait for 'Done (', then feed "carpet", "carpet list", "stop" via stdin.
# ASCII-only on purpose: powershell.exe reads .ps1 as ANSI when there is no BOM.
$ErrorActionPreference = 'Continue'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'

$root   = Split-Path $PSScriptRoot -Parent
$proj   = Join-Path $root 'cca-neoforge'
$outLog = Join-Path $PSScriptRoot 'out\runServer_probe.log'
New-Item -ItemType Directory -Force -Path (Split-Path $outLog) | Out-Null
Set-Content -LiteralPath $outLog -Value '' -Encoding UTF8

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName               = "$env:ComSpec"
$psi.Arguments              = '/c gradlew.bat runServer --console=plain'
$psi.WorkingDirectory       = $proj
$psi.UseShellExecute        = $false
$psi.RedirectStandardInput  = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError  = $false
$psi.CreateNoWindow         = $true

$p = [System.Diagnostics.Process]::Start($psi)
if ($null -eq $p) { Write-Host 'PROBE_DONE failed to start process'; exit 1 }

$started   = $false
$sentRest  = $false
$deadline  = (Get-Date).AddSeconds(420)

while ((Get-Date) -lt $deadline) {
    $line = $p.StandardOutput.ReadLine()
    if ($null -eq $line) { break }
    if ($line.Trim().Length -gt 0) { Add-Content -LiteralPath $outLog -Value $line -Encoding UTF8 }

    if (-not $started -and $line -match 'Done \(') {
        $started = $true
        Start-Sleep -Seconds 2
        $p.StandardInput.WriteLine('carpet')
        $p.StandardInput.Flush()
        continue
    }
    if ($started -and -not $sentRest) {
        $sentRest = $true
        Start-Sleep -Seconds 4
        $p.StandardInput.WriteLine('carpet list')
        $p.StandardInput.Flush()
        Start-Sleep -Seconds 4
        $p.StandardInput.WriteLine('stop')
        $p.StandardInput.Flush()
    }
    if ($started -and $line -match 'Stopping server') { Start-Sleep -Seconds 5; break }
}

if (-not $p.HasExited) { try { $p.Kill() } catch { } }
Write-Host "PROBE_DONE started=$started log=$outLog"
