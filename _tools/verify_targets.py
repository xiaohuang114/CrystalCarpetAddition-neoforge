# -*- coding: utf-8 -*-
"""Verify every refmap target descriptor against the real Mojmap class dump.

For each `Lowner;name(args)ret` in _mappings/refmap_mojmap.md, check whether the
declaring class in _mappings/mojang-1.21.1-client.txt (1.21.1 client dump) really
has that member.  Falls back to a global search (inherited / declared elsewhere).
Writes analysis/_verify_targets.txt.  Read-only.
"""
import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else r"D:\Minecraft-mod-development\cca移植neoforge")
REFMAP = ROOT / "_mappings" / "refmap_mojmap.md"
MOJ_TXT = ROOT / "_mappings" / "mojang-1.21.1-client.txt"

DESC = re.compile(r"L(?P<owner>[^;]+);(?P<name>[\w$<>]+)\((?P<args>[^)]*)\)(?P<ret>[^`\s]+)")
FIELD = re.compile(r"L(?P<owner>[^;]+);(?P<name>[\w$]+):L(?P<type>[^;]+);")

PRIM2JAVA = {"V": "void", "Z": "boolean", "B": "byte", "C": "char", "S": "short",
             "I": "int", "J": "long", "F": "float", "D": "double"}


def jvm_types(s):
    """'FFLnet/minecraft/x;' -> ['float','float','net.minecraft.x']"""
    out, i, n = [], 0, len(s)
    while i < n:
        dims = 0
        while i < n and s[i] == "[":
            dims += 1
            i += 1
        if i >= n:
            break
        if s[i] == "L":
            j = s.index(";", i)
            base = s[i + 1:j].replace("/", ".")
            i = j + 1
        else:
            base = PRIM2JAVA.get(s[i], s[i])
            i += 1
        out.append(base + "[]" * dims)
    return out


def norm_type(t):
    t = t.strip()
    dims = ""
    while t.endswith("[]"):
        t = t[:-2]
        dims += "[]"
    if t.startswith("L") and t.endswith(";"):
        t = t[1:-1]
    if t in PRIM2JAVA:
        return PRIM2JAVA[t] + dims
    return t.replace("/", ".") + dims


# ---------- real class dump ----------
decls = {}
cls_re = re.compile(r"^(?P<moj>[\w.$]+) -> (?P<obf>[\w$]+):$")
mem_re = re.compile(r"^ {4}(?:\d+:\d+:)?(?P<ret>[\w.$\[\]]+) (?P<name>[\w$<>]+)\((?P<args>.*)\) -> (?P<obf>[\w$]+)$")
fld_re = re.compile(r"^ {4}(?P<type>[\w.$\[\]]+) (?P<name>[\w$]+) -> (?P<obf>[\w$]+)$")
cur = None
for line in MOJ_TXT.read_text(encoding="utf-8").splitlines():
    if line.startswith("#") or not line.strip():
        continue
    if not line.startswith(" "):
        m = cls_re.match(line)
        if m:
            cur = m.group("moj")
            decls.setdefault(cur, set())
        continue
    m = mem_re.match(line)
    if m:
        args = tuple(a.strip() for a in m.group("args").split(",") if a.strip())
        decls[cur].add((m.group("name"), args, m.group("ret")))
        continue
    m = fld_re.match(line)
    if m:
        decls[cur].add((m.group("name"), (), m.group("type")))

for owner in decls:
    decls[owner] = set(decls[owner])

global_index = {}
for owner, members in decls.items():
    for (n, a, r) in members:
        global_index.setdefault((n, a, r), []).append(owner)

# ---------- refmap ----------
rows, section = [], None
for line in REFMAP.read_text(encoding="utf-8").splitlines():
    if line.startswith("## "):
        section = line[3:].strip()
        continue
    if "`" not in line or "inter:" in line:
        continue
    for m in DESC.finditer(line):
        rows.append((section, "M", m.group("owner").replace("/", "."), m.group("name"),
                     tuple(jvm_types(m.group("args"))), norm_type(m.group("ret"))))
    if "(" not in line:
        for m in FIELD.finditer(line):
            rows.append((section, "F", m.group("owner").replace("/", "."), m.group("name"),
                         (), norm_type(m.group("type"))))


def check(owner, name, args, ret):
    if owner in decls:
        if (name, args, ret) in decls[owner]:
            return "OK", ""
        for (n2, a2, r2) in decls[owner]:
            if n2 == name and len(a2) == len(args):
                return "ARGC?", "declared as %s(%s)%s" % (name, ",".join(a2), r2)
    hits = global_index.get((name, args, ret), [])
    if hits:
        return "INHERIT", "declared in " + ", ".join(h.rsplit(".", 1)[-1] for h in hits[:3])
    return "MISSING", "no declaration anywhere with this signature"


out, tally = [], {}
for section, kind, owner, name, args, ret in rows:
    st, note = check(owner, name, args, ret)
    tally[st] = tally.get(st, 0) + 1
    out.append("%-8s %-46s %s.%s(%s)%s%s" % (
        st, section, owner.rsplit(".", 1)[-1], name,
        ",".join(a.rsplit(".", 1)[-1] for a in args) or "", ret.rsplit(".", 1)[-1],
        ("   << " + note) if note else ""))

head = "refmap targets=%d  " % len(rows) + "  ".join("%s=%d" % (k, v) for k, v in sorted(tally.items()))
(ROOT / "analysis" / "_verify_targets.txt").write_text(head + "\n" + "\n".join(out), encoding="utf-8")
print(head)
print()
for l in out:
    print(l)
