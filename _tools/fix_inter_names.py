import os
import re
import sys

D = r"D:\Minecraft-mod-development\cca移植neoforge\_mappings"

# inter member name -> set(mojmap name);  inter class -> moj class
by_inter = {}
global_names = {}
cur_inter = None
for line in open(os.path.join(D, "inter_to_moj.tiny"), encoding="utf-8"):
    line = line.rstrip("\n")
    if line.startswith("c\t"):
        p = line.split("\t")
        cur_inter = p[1]
        continue
    if not line.startswith("\t") or cur_inter is None:
        continue
    p = line.split("\t")
    kind = p[1]
    if kind not in ("f", "m"):
        continue
    inter_name, moj_name = p[3], p[4]
    by_inter.setdefault((cur_inter, kind, inter_name), set()).add(moj_name)
    global_names.setdefault(inter_name, set()).add(moj_name)

tok = re.compile(r"\b(field_\d+|method_\d+|class_\d+)\b")


def fix_file(path):
    src = open(path, encoding="utf-8").read()
    unresolved = []
    ambig = []

    def repl(m):
        name = m.group(1)
        cands = global_names.get(name)
        if not cands:
            unresolved.append(name)
            return name
        if len(cands) > 1:
            ambig.append((name, sorted(cands)))
            return name
        return list(cands)[0]

    out = tok.sub(repl, src)
    if out != src:
        open(path, "w", encoding="utf-8", newline="\n").write(out)
    return unresolved, ambig


roots = sys.argv[1:]
for root in roots:
    for dirpath, _dirs, files in os.walk(root):
        for f in files:
            if f.endswith(".java"):
                p = os.path.join(dirpath, f)
                u, a = fix_file(p)
                if u or a:
                    print("== " + p.replace(D, ""))
                    if u:
                        print("   unresolved: " + ", ".join(sorted(set(u))))
                    for name, cands in a:
                        print("   ambiguous: %s -> %s" % (name, cands))
