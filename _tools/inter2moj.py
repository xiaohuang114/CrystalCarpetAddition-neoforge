import os, re, sys

D = r"D:\Minecraft-mod-development\cca绉绘neoforge\_mappings"

# obf<->inter
obf2inter_cls, inter2obf_cls = {}, {}
obf2inter_mem, inter2obf_mem = {}, {}   # key: (obfCls, kind, obfName) / (interCls, kind, interName)
cur = None
for line in open(os.path.join(D, "inter", "mappings", "mappings.tiny"), encoding="utf-8"):
    line = line.rstrip("\n")
    if line.startswith("c\t"):
        p = line.split("\t")
        obf, inter = p[1], p[2]
        cur = (obf, inter)
        obf2inter_cls[obf] = inter
        inter2obf_cls[inter] = obf
    elif line.startswith(("\tm\t", "\tf\t")) and cur:
        p = line.split("\t")
        kind = p[1][0]  # 'm' or 'f'
        obfName, interName = p[2], p[3]
        obf2inter_mem[(cur[0], kind, obfName)] = interName
        inter2obf_mem[(cur[1], kind, interName)] = obfName

# obf -> moj
obf2moj_cls, moj2obf_cls = {}, {}
moj_mem = {}   # (obfCls, kind, obfName) -> (mojName, desc)
cur_obf = None
hdr = re.compile(r"^(\S+) -> (\S+):$")
fld = re.compile(r"^    (\S+) (\S+) -> (\S+)$")
mth = re.compile(r"^    (?:\d+:\d+:)?(\S+) (\S+)\(([^)]*)\) -> (\S+)$")
for line in open(os.path.join(D, "mojang-1.21.1-client.txt"), encoding="utf-8"):
    line = line.rstrip("\n")
    m = hdr.match(line)
    if m:
        obf2moj_cls[m.group(2)] = m.group(1)
        moj2obf_cls[m.group(1)] = m.group(2)
        cur_obf = m.group(2)
        continue
    if not line.startswith("    "):
        continue
    m = mth.match(line)
    if m:
        moj_mem[(cur_obf, "m", m.group(4))] = (m.group(2), "(" + m.group(3) + ")")
        continue
    m = fld.match(line)
    if m:
        moj_mem[(cur_obf, "f", m.group(3))] = (m.group(2), m.group(1))


def inter_to_moj(moj_class_dotted, inter_name, kind):
    """mojClass dotted, intermediary member name, 'f' or 'm' -> mojmap member name or None"""
    moj_class = moj_class_dotted
    obf_cls = moj2obf_cls.get(moj_class)
    if obf_cls is None:
        return None, "class not found in mojang map"
    inter_cls = obf2inter_cls.get(obf_cls)
    if inter_cls is None:
        return None, "no intermediary class"
    obf_name = inter2obf_mem.get((inter_cls, kind, inter_name))
    if obf_name is None:
        return None, "no obf name for inter %s" % inter_name
    hit = moj_mem.get((obf_cls, kind, obf_name))
    if hit is None:
        return None, "no moj name for obf %s" % obf_name
    return hit[0], hit[1]


def dump_class(moj_class_dotted):
    moj_class = moj_class_dotted
    obf_cls = moj2obf_cls.get(moj_class)
    print("\n##### %s (obf=%s)" % (moj_class_dotted, obf_cls))
    if obf_cls is None:
        print("  NOT FOUND")
        return
    inter_cls = obf2inter_cls.get(obf_cls, "?")
    rows = [(k, v) for k, v in moj_mem.items() if k[0] == obf_cls]
    for (c, kind, obf_name), (moj_name, desc) in sorted(rows, key=lambda r: (r[0][1], r[1][0])):
        inter_name = obf2inter_mem.get((obf_cls, kind, obf_name), "-")
        print("  %s %-6s inter=%-16s moj=%s%s" % (kind, obf_name, inter_name, moj_name, desc))


if __name__ == "__main__":
    args = sys.argv[1:]
    if args and args[0] == "dump":
        for cls in args[1:]:
            dump_class(cls)
    else:
        # queries: Class:kind:interName  (kind f/m)
        for q in args:
            cls, kind, name = q.split(":")
            val, info = inter_to_moj(cls, name, kind)
            if val:
                print("%s %s %s -> %s" % (cls, kind, name, val))
            else:
                print("%s %s %s -> ??? (%s)" % (cls, kind, name, info))

