import sys
ids = """method_16478 method_37908 method_16219 method_19502 method_24515 method_6118 field_6169 method_5529 method_7960 method_6112 field_16595 method_5578 method_6111 method_15340 method_8450 method_8355 field_19422 method_6092 method_35057 method_6181 method_7973 method_16515 method_56673 method_46762 field_41252 method_7325 method_51469 method_8407 field_5801 method_19500 method_19495 method_16540""".split()
want = set(ids)
res = {}
cur_cls = None
with open(r"_mappings\yarn\mappings\mappings.tiny", encoding="utf-8") as f:
    for line in f:
        if line.startswith("c\t"):
            p = line.rstrip("\n").split("\t")
            cur_cls = (p[1], p[2] if len(p) > 2 else p[1])
            continue
        if not line.startswith("\t"): 
            continue
        p = line.rstrip("\n").split("\t")
        if len(p) < 5: continue
        kind, desc, src, dst = p[1], p[2], p[3], p[4]
        if src in want:
            res.setdefault(src, set()).add((cur_cls[1], kind, dst, desc))
for i in ids:
    v = res.get(i)
    if not v:
        print(f"{i}: NOT FOUND")
    else:
        for owner, kind, dst, desc in sorted(v):
            print(f"{i}: {owner}.{dst} {desc}  [{kind}]")
