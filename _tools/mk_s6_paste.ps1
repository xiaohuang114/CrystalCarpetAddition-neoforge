# mk_s6_paste.ps1 - assemble prompts\S6-PASTE.md from existing parts.
# ASCII-only source on purpose: this box decodes BOM-less .ps1 as ANSI, so non-ASCII literals inside
# the script get mangled. Chinese titles and the three Chinese file names live in
# _tools\s6_titles.txt and _tools\s6_paths.txt, both read as UTF-8.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$prompts = Join-Path $root 'prompts'
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Get-Md([string]$path) {
    [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
}

$titles = (Get-Md (Join-Path $PSScriptRoot 's6_titles.txt')).Trim() -split "`r?`n"
$paths = (Get-Md (Join-Path $PSScriptRoot 's6_paths.txt')).Trim() -split "`r?`n"
if ($titles.Count -lt 5) { throw "titles file has $($titles.Count) lines, expected 5" }
if ($paths.Count -lt 3) { throw "paths file has $($paths.Count) lines, expected 3" }
foreach ($p in $paths) {
    if (-not (Test-Path -LiteralPath (Join-Path $root $p.Trim()))) { throw "missing: $p" }
}

$s5 = Get-Md (Join-Path $prompts 'S5-PASTE.md')
$parts = [regex]::Split($s5, '={10,}\s*\[[0-9]/5\][^\r\n]*={10,}')
if ($parts.Count -lt 6) { throw "S5-PASTE split into $($parts.Count) parts, expected 6" }

$bodies = @(
    $parts[1].Trim("`r", "`n"),
    (Get-Md (Join-Path $root $paths[0].Trim())),
    $parts[3].Trim("`r", "`n"),
    (Get-Md (Join-Path $root $paths[1].Trim())),
    (Get-Md (Join-Path $root $paths[2].Trim()))
)

$sb = New-Object System.Text.StringBuilder
for ($i = 0; $i -lt 5; $i++) {
    if ($i -gt 0) { [void]$sb.AppendLine(); [void]$sb.AppendLine() }
    [void]$sb.Append('===================== [' + ($i + 1) + '/5] ' + $titles[$i] + ' =====================')
    [void]$sb.AppendLine()
    [void]$sb.AppendLine()
    [void]$sb.AppendLine($bodies[$i].TrimEnd())
}

$out = Join-Path $prompts 'S6-PASTE.md'
[System.IO.File]::WriteAllText($out, $sb.ToString(), $utf8)
$text = Get-Md $out
"seg2 src = $($paths[0].Trim())"
"seg4 src = $($paths[1].Trim())"
"seg5 src = $($paths[2].Trim())"
"out      = $out"
"chars    = $($text.Length)"
"lines    = $(($text -split "`n").Count)"
for ($i = 0; $i -lt 5; $i++) {
    "seg {0} = {1,6} chars {2,5} lines  {3}" -f ($i + 1), $bodies[$i].Length, (($bodies[$i] -split "`n").Count), $titles[$i]
}
