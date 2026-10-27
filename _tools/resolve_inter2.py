# -*- coding: utf-8 -*-
"""intermediary -> Mojmap member resolution for the CCA port (s0).

Chain: intermediary tiny (official<->intermediary) + mojang txt (mojmap->official),
joined on (obfOwner, obfName, argCount, kind).  Read-only.
"""
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else r"D:\Minecraft-mod-development\cca移植neoforge")
INTER_JAR = ROOT / "_mappings" / "intermediary-v2.jar"
MOJ_TXT = ROOT / "_mappings" / "mojang-1.21.1-client.txt"
SRC = ROOT / "_decomp" / "mojmap" / "crystal0404" / "crystalcarpetaddition"

TOKEN = re.compile(r"\b((?:method|field|class)_\d+)\b")


def argcount(desc):
    if "(" not in desc:
        return 0
    inner = desc[desc.index("(") + 1: desc.rindex(")")]
    if not inner:
        return 0
    n, depth = 0, 0
    for ch in inner:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        elif ch == "," and depth == 0:
            n += 1
    return n + 1


# ---------- 1. intermediary tiny: official -> intermediary ----------
inter2obf_class = {}
obf2inter_class = {}
key_obf2inter = {}
with zipfile.ZipFile(INTER_JAR) as z:
    raw = z.read("mappings/mappings.tiny").decode("utf-8")
owner_obf = None
for line in raw.splitlines():
    if line.startswith("c\t"):
        p = line.split("\t")
        owner_obf = p[1]
        obf2inter_class[p[1]] = p[2]
        inter2obf_class[p[2]] = p[1]
    elif line.startswith("\t") and not line.startswith("\t\t"):
        p = line[1:].split("\t")
        if len(p) < 4:
            continue
        kind, desc, obf, inter = p[0].strip(), p[1], p[2], p[3]
        key_obf2inter[(owner_obf, obf, argcount(desc), kind)] = (inter, owner_obf)

# ---------- 2. mojang txt: mojmap -> official ----------
key_obf2moj = {}
cls_re = re.compile(r"^(?P<moj>[\w.$]+) -> (?P<obf>[\w$]+):$")
mem_re = re.compile(r"^ {4}(?:\d+:\d+:)?(?P<ret>[\w.$\[\]]+) (?P<name>[\w$<>]+)\((?P<args>.*)\) -> (?P<obf>[\w$]+)$")
fld_re = re.compile(r"^ {4}(?P<type>[\w.$\[\]]+) (?P<name>[\w$]+) -> (?P<obf>[\w$]+)$")
cur_obf = cur_moj = None
for line in MOJ_TXT.read_text(encoding="utf-8").splitlines():
    if line.startswith("#") or not line.strip():
        continue
    if not line.startswith(" "):
        m = cls_re.match(line)
        if m:
            cur_moj, cur_obf = m.group("moj"), m.group("obf")
        continue
    m = mem_re.match(line)
    if m:
        key_obf2moj[(cur_obf, m.group("obf"), argcount("(" + m.group("args") + ")"), "m")] = (m.group("name"), cur_moj)
        continue
    m = fld_re.match(line)
    if m:
        key_obf2moj[(cur_obf, m.group("obf"), 0, "f")] = (m.group("name"), cur_moj)

# ---------- 3. join: inter name -> moj candidates ----------
inter2moj = {}
for k, (inter, _o) in key_obf2inter.items():
    hit = key_obf2moj.get(k)
    if hit:
        inter2moj.setdefault(inter, set()).add((hit[0], hit[1]))

# ---------- 4. scan sources ----------
out_lines = []
all_tokens = set()
files = 0
for f in sorted(SRC.rglob("*.java")):
    hits = {}
    for i, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
        for m in TOKEN.finditer(line):
            hits.setdefault(m.group(1), []).append(i)
            all_tokens.add(m.group(1))
    if not hits:
        continue
    files += 1
    rel = str(f.relative_to(SRC)).replace("\\", "/")
    out_lines.append("")
    out_lines.append("## " + rel)
    for tok, lns in hits.items():
        where = ",".join(map(str, lns))
        if tok.startswith("class_"):
            out_lines.append("  %-14s L%-14s -> CLASS %s" % (tok, where, obf2inter_class.get(tok, "?")))
            continue
        cands = inter2moj.get(tok, set())
        names = sorted({c[0] for c in cands})
        if not cands:
            out_lines.append("  %-14s L%-14s -> !! UNRESOLVED" % (tok, where))
        else:
            owners = sorted({c[1].rsplit(".", 1)[-1] for c in cands})
            out_lines.append("  %-14s L%-14s -> %-34s owners=%s" % (tok, where, "|".join(names), ",".join(owners[:4])))

header = "files=%d tokens=%d" % (files, len(all_tokens))
out = ROOT / "analysis" / "_resolve2.txt"
out.write_text(header + "\n" + "\n".join(out_lines), encoding="utf-8")
print(header)
print("written:", out)
print("unresolved:", sum(1 for l in out_lines if "UNRESOLVED" in l))
