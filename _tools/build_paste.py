"""拼装 prompts\\S{N}-PASTE.md：复用前一版 PASTE 的 [1/5] 00 与 [3/5] FACTS 段，其余读文件原文。
用法：python _tools\\build_paste.py 8
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROMPTS = ROOT / "prompts"

n = sys.argv[1] if len(sys.argv) > 1 else "8"
prev = PROMPTS / f"S{int(n) - 1}-PASTE.md"
out = PROMPTS / f"S{n}-PASTE.md"

text = prev.read_text(encoding="utf-8")
parts = re.split(r"={10,}\s*\[[0-9]/5\][^\r\n]*={10,}", text)
# parts[0] 是第一个分隔行之前的内容（通常为空），之后依次是 5 段
assert len(parts) >= 6, f"分割失败：{len(parts)} 段"
sys_prompt = parts[1].strip("\r\n")
facts = parts[3].strip("\r\n")

errata = (PROMPTS / f"s{int(n) - 1}-验收结论与勘误.md").read_text(encoding="utf-8").strip()
digest = (PROMPTS / f"s{int(n) - 1}-HANDOFF摘要.md").read_text(encoding="utf-8").strip()
cands = [
    p for p in sorted(PROMPTS.glob(f"s{n}-*.md"))
    if "验收结论" not in p.name and "HANDOFF摘要" not in p.name
]
assert len(cands) == 1, f"任务书候选不唯一：{[p.name for p in cands]}"
book = cands[0].read_text(encoding="utf-8").strip()

sep = lambda i, t: f"===================== [{i}/5] {t} ====================="
buf = "\n\n".join([
    sep(1, "系统提示词"), sys_prompt,
    sep(2, f"s{int(n) - 1} 验收结论与勘误"), errata,
    sep(3, "FACTS 节选"), facts,
    sep(4, f"HANDOFF-s{int(n) - 1} 摘要"), digest,
    sep(5, f"s{n} 任务书"), book,
]) + "\n"
out.write_text(buf, encoding="utf-8")

lines = buf.count("\n")
sections = [len(x) for x in (sys_prompt, errata, facts, digest, book)]
print(f"written {out.name}: {len(buf)} chars / {lines} lines")
print("section char counts:", sections)
