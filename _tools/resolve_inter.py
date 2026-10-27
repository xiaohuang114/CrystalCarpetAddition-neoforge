# -*- coding: utf-8 -*-
"""Resolve intermediary tokens (method_/field_/class_) found in _decomp/mojmap/**/*.java
against _mappings/inter_to_moj.tiny (intermediary -> named/mojmap).
Read-only helper for s0 analysis output."""
import json
import re
import sys
from pathlib import Path

ROOT = Path(r"D:\Minecraft-mod-development\cca移植neoforge")
TINY = ROOT / "_mappings" / "inter_to_moj.tiny"
SRC = ROOT / "_decomp" / "mojmap" / "crystal0404" / "crystalcarpetaddition"

TOKEN = re.compile(r"\b((?:method|field|class)_\d+)\b")

# ---------- parse tiny v2 ----------
cls_moj = {}          # class_xxxx -> moj path
members = {}          # member_inter -> list of (owner_moj, name, desc, kind)
owner_cls = None
owner_moj = None
for raw in TINY.read_text(encoding="utf-8").splitlines():
    if raw.startswith("c\t"):
        parts = raw.split("\t")
        owner_cls, owner_moj = parts[1], parts[2]
        cls_moj[owner_cls] = owner_moj
    elif raw.startswith("\t") and not raw.startswith("\t\t"):
        parts = raw[1:].split("\t")
        if len(parts) < 4:
            continue
        kind, desc, inter, moj = parts[0].strip(), parts[1], parts[2], parts[3]
        members.setdefault(inter, []).append((owner_moj, moj, desc, kind))

# ---------- scan files ----------
report = []
tot_tokens = set()
for f in sorted(SRC.rglob("*.java")):
    rel = str(f.relative_to(SRC)).replace("\\", "/")
    lines = f.read_text(encoding="utf-8").splitlines()
    hits = {}
    for i, line in enumerate(lines, 1):
        for m in TOKEN.finditer(line):
            tok = m.group(1)
            hits.setdefault(tok, []).append(i)
            tot_tokens.add(tok)
    if hits:
        report.append((rel, hits))

print("=== files with intermediary tokens: %d, distinct tokens: %d ===" % (len(report), len(tot_tokens)))
for rel, hits in report:
    print("\n## " + rel)
    for tok, lns in hits.items():
        cands = members.get(tok, [])
        if tok.startswith("class_"):
            print("  %-14s L%-12s -> %s" % (tok, ",".join(map(str, lns)), cls_moj.get(tok, "??")))
            continue
        # dedupe by (owner short, name)
        uniq = []
        for owner, name, desc, kind in cands:
            short = owner.rsplit("/", 1)[-1]
            key = (short, name, desc)
            if key not in uniq:
                uniq.append(key)
        if not uniq:
            print("  %-14s L%-12s -> !! NOT IN TINY" % (tok, ",".join(map(str, lns))))
        else:
            names = sorted({u[1] for u in uniq})
            if len(names) == 1:
                print("  %-14s L%-12s -> %s   [%d owner]" % (tok, ",".join(map(str, lns)), names[0], len(uniq)))
            else:
                detail = "; ".join("%s.%s%s" % (o, n, d) for o, n, d in uniq[:14])
                more = "" if len(uniq) <= 14 else " ...(+%d)" % (len(uniq) - 14)
                print("  %-14s L%-12s -> AMBIGUOUS %s : %s%s" % (tok, ",".join(map(str, lns)), names, detail, more))
