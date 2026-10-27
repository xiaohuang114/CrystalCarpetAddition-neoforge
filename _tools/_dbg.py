import sys
from pathlib import Path
ROOT=Path(r"D:\Minecraft-mod-development\cca移植neoforge")
src=(ROOT/"_tools"/"resolve_inter3.py").read_text(encoding="utf-8")
ns={"__name__":"m"}
exec(compile(src,"r","exec"), ns)
km=ns["key_moj"]; ki=ns["key_inter"]; m2o=ns["moj2obf_class"]
print("len key_moj:",len(km),"len key_inter:",len(ki))
k=('dtb','a',('dtc','dcw','jd','dtc','Z'),'V','m')
print("probe in key_moj:", k in km, km.get(k))
print("samples:", list(km.items())[:3])
# how many moj keys have owner dtb
print("dtb keys:", [x for x in km if x[0]=="dtb"][:5])
