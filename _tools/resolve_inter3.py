# -*- coding: utf-8 -*-
"""intermediary -> Mojmap member resolution for the CCA port (s0).

Chain: intermediary tiny (official<->intermediary) + mojang txt (mojmap->official),
joined on the FULL canonical descriptor in obf space:
    (obfClass, obfMemberName, tuple(argTypes), retType, kind)
Read-only helper; writes analysis/_resolve2.txt.
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
PRIM = set("VZBCSIJFD")
JAVA_PRIM = {"void": "V", "boolean": "Z", "byte": "B", "char": "C", "short": "S",
             "int": "I", "long": "J", "float": "F", "double": "D"}


def split_top(s):
    """split a comma separated java type list, honouring <...>."""
    if not s:
        return []
    out, depth, cur = [], 0, []
    for ch in s:
        if ch == "<":
            depth += 1
            cur.append(ch)
        elif ch == ">":
            depth -= 1
            cur.append(ch)
        elif ch == "," and depth == 0:
            out.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    if cur:
        out.append("".join(cur))
    return out


def parse_jvm_types(s):
    """parse a JVM type sequence like 'IILdtc;Z' -> ['I','I','dtc','Z']."""
    out, i, n = [], 0, len(s)
    while i < n:
        dims = 0
        while i < n and s[i] == "[":
            dims += 1
            i += 1
        if i >= n:
            break
        ch = s[i]
        if ch == "L":
            j = s.index(";", i)
            base = s[i + 1:j]
            i = j + 1
        else:
            base = ch
            i += 1
        out.append(base + "[]" * dims)
    return out


def jvm_desc_parts(desc):
    if "(" in desc:
        args_s = desc[desc.index("(") + 1:desc.rindex(")")]
        ret_s = desc[desc.rindex(")") + 1:]
        args = parse_jvm_types(args_s)
        ret = parse_jvm_types(ret_s)
        return tuple(args), (ret[0] if ret else "V")
    return (), parse_jvm_types(desc)[0]


def norm_obf_type(t, moj2obf):
    t = t.strip()
    dims = ""
    while t.endswith("[]"):
        t = t[:-2]
        dims += "[]"
    if t in JAVA_PRIM:
        return JAVA_PRIM[t] + dims
    if t in PRIM:
        return t + dims
    if t in moj2obf:
        return moj2obf[t] + dims
    return t.replace(".", "/") + dims


# ---------- 1. intermediary tiny: official -> intermediary ----------
obf2inter_class = {}
key_inter = {}
owner_obf = None
with zipfile.ZipFile(INTER_JAR) as z:
    raw = z.read("mappings/mappings.tiny").decode("utf-8")
for line in raw.splitlines():
    if line.startswith("c\t"):
        p = line.split("\t")
        owner_obf = p[1]
        obf2inter_class[p[1]] = p[2]
    elif line.startswith("\t") and not line.startswith("\t\t"):
        p = line[1:].split("\t")
        if len(p) < 4:
            continue
        kind, desc, obf, inter = p[0].strip(), p[1], p[2], p[3]
        args, ret = jvm_desc_parts(desc)
        key_inter[(owner_obf, obf, args, ret, kind)] = inter

# ---------- 2. mojang txt: mojmap -> official ----------
lines = MOJ_TXT.read_text(encoding="utf-8").splitlines()
moj2obf_class = {}
cls_re = re.compile(r"^(?P<moj>[\w.$]+) -> (?P<obf>[\w$]+):$")
mem_re = re.compile(r"^ {4}(?:\d+:\d+:)?(?P<ret>[\w.$\[\]]+) (?P<name>[\w$<>]+)\((?P<args>.*)\) -> (?P<obf>[\w$]+)$")
fld_re = re.compile(r"^ {4}(?P<type>[\w.$\[\]]+) (?P<name>[\w$]+) -> (?P<obf>[\w$]+)$")
for line in lines:
    if line.startswith("#") or not line.strip() or line.startswith(" "):
        continue
    m = cls_re.match(line)
    if m:
        moj2obf_class[m.group("moj")] = m.group("obf")

key_moj = {}
cur_obf = None
for line in lines:
    if line.startswith("#") or not line.strip():
        continue
    if not line.startswith(" "):
        m = cls_re.match(line)
        cur_obf = m.group("obf") if m else None
        continue
    m = mem_re.match(line)
    if m:
        args = tuple(norm_obf_type(a, moj2obf_class) for a in split_top(m.group("args")))
        ret = norm_obf_type(m.group("ret"), moj2obf_class)
        key_moj.setdefault((cur_obf, m.group("obf"), args, ret, "m"), m.group("name"))
        continue
    m = fld_re.match(line)
    if m:
        key_moj.setdefault((cur_obf, m.group("obf"), (), norm_obf_type(m.group("type"), moj2obf_class), "f"), m.group("name"))

# ---------- 3. join ----------
inter2moj = {}
for k, inter in key_inter.items():
    hit = key_moj.get(k)
    if hit:
        inter2moj.setdefault(inter, set()).add(hit)

# ---------- 4. scan sources ----------
out_lines, all_tokens, files = [], set(), 0
for f in sorted(SRC.rglob("*.java")):
    hits = {}
    for i, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
        for m in TOKEN.finditer(line):
            hits.setdefault(m.group(1), []).append(i)
            all_tokens.add(m.group(1))
    if not hits:
        continue
    files += 1
    out_lines.append("")
    out_lines.append("## " + str(f.relative_to(SRC)).replace("\\", "/"))
    for tok, lns in hits.items():
        where = ",".join(map(str, lns))
        if tok.startswith("class_"):
            out_lines.append("  %-14s L%-14s -> CLASS %s" % (tok, where, obf2inter_class.get(tok, "?")))
            continue
        names = sorted(inter2moj.get(tok, set()))
        if not names:
            out_lines.append("  %-14s L%-14s -> !! UNRESOLVED" % (tok, where))
        else:
            out_lines.append("  %-14s L%-14s -> %s" % (tok, where, "|".join(names)))

unres = len(all_tokens) - len([t for t in all_tokens if inter2moj.get(t)])
header = "files=%d tokens=%d resolved=%d unresolved=%d" % (files, len(all_tokens), len(all_tokens) - unres, unres)
out = ROOT / "analysis" / "_resolve2.txt"
out.write_text(header + "\n" + "\n".join(out_lines), encoding="utf-8")
print(header)
print("!UNRESOLVED lines:", sum(1 for l in out_lines if "UNRESOLVED" in l))
