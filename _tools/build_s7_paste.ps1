$ErrorActionPreference = 'Stop'
$root = 'D:\Minecraft-mod-development\cca移植neoforge'
$prompts = Join-Path $root 'prompts'

function Get-Md([string]$name) {
    return [System.IO.File]::ReadAllText((Join-Path $prompts $name), [System.Text.Encoding]::UTF8)
}

$prev = Get-Md 'S6-PASTE.md'
$parts = [regex]::Split($prev, '={10,}\s*\[[0-9]/5\][^\r\n]*={10,}')
Write-Host ("S6-PASTE segments: " + $parts.Count)
if ($parts.Count -lt 6) { throw 'S6-PASTE.md 分段数量异常' }

$sys  = $parts[1].Trim()
$fact = $parts[3].Trim()

$sep = '===================== [{0}/5] {1} ====================='

$sb = New-Object System.Text.StringBuilder
function Add-Block([string]$header, [string]$body) {
    [void]$script:sb.AppendLine($header)
    [void]$script:sb.AppendLine()
    [void]$script:sb.AppendLine($body.Trim())
    [void]$script:sb.AppendLine()
}

Add-Block ($sep -f 1, '系统提示词（硬约束 + 输出格式）') $sys
Add-Block ($sep -f 2, 's6 验收结论与勘误 L1-L13') (Get-Md 's6-验收结论与勘误.md')
Add-Block ($sep -f 3, 'FACTS 节选（物料路径 / 项目结构 / 机械转换规则 / 已知坑）') $fact
Add-Block ($sep -f 4, 'HANDOFF-s6 摘要') (Get-Md 's6-HANDOFF摘要.md')
Add-Block ($sep -f 5, 's7 任务书（箱子规则批）') (Get-Md 's7-箱子规则批.md')

$out = Join-Path $prompts 'S7-PASTE.md'
[System.IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))

$text = [System.IO.File]::ReadAllText($out, [System.Text.Encoding]::UTF8)
Write-Host ("WROTE " + $out)
Write-Host ("CHARS " + $text.Length)
Write-Host ("LINES " + ($text -split "`n").Count)
Write-Host ("SEPARATORS " + ([regex]::Matches($text, '={10,}\s*\[[0-9]/5\]')).Count)
