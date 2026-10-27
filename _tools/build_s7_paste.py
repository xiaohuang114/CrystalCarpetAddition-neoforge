# -*- coding: utf-8 -*-
"""Assemble prompts/S7-PASTE.md from segments 1 & 3 of S6-PASTE.md plus s6 errata and s7 book."""
import io
import os
import re

ROOT = r"D:\Minecraft-mod-development\cca移植neoforge"
PROMPTS = os.path.join(ROOT, "prompts")


def read(name):
    path = os.path.join(PROMPTS, name)
    with io.open(path, "r", encoding="utf-8") as fh:
        return fh.read()


prev = read("S6-PASTE.md")
parts = re.split(r"={10,}\s*\[[0-9]/5\][^\r\n]*={10,}", prev)
print("S6-PASTE segments:", len(parts))
assert len(parts) >= 6, "S6-PASTE.md 分段数量异常"

sys_prompt = parts[1].strip()
facts = parts[3].strip()

SEP = "===================== [{0}/5] {1} ====================="

blocks = [
    (SEP.format(1, "系统提示词（硬约束 + 输出格式）"), sys_prompt),
    (SEP.format(2, "s6 验收结论与勘误 L1-L13"), read("s6-验收结论与勘误.md")),
    (SEP.format(3, "FACTS 节选（物料路径 / 项目结构 / 机械转换规则 / 已知坑）"), facts),
    (SEP.format(4, "HANDOFF-s6 摘要"), read("s6-HANDOFF摘要.md")),
    (SEP.format(5, "s7 任务书（箱子规则批）"), read("s7-箱子规则批.md")),
]

out_text = "\n".join("{0}\n\n{1}\n".format(h, b.strip()) for h, b in blocks)
out_path = os.path.join(PROMPTS, "S7-PASTE.md")
with io.open(out_path, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(out_text)

print("WROTE", out_path)
print("CHARS", len(out_text))
print("LINES", out_text.count("\n") + 1)
print("SEPARATORS", len(re.findall(r"={10,}\s*\[[0-9]/5\]", out_text)))
for h, b in blocks:
    print("  seg", h.split("[")[1].split("]")[0], "chars", len(b.strip()))
