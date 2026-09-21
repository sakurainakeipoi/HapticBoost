#!/usr/bin/env python3
# 生成二进制 AndroidManifest.xml (AXML)，与 aapt 输出结构对齐
#  - UTF-16 字符串池 (flags=0)
#  - 属性名字符串排在字符串池最前，资源映射表只覆盖这些
#  - namespace 节点含 lineNumber/comment
import struct
import sys

NS_PREFIX = "android"
NS_URI = "http://schemas.android.com/apk/res/android"

ATTR_ID = {
    "theme": 0x01010000,
    "label": 0x01010001,
    "icon": 0x01010002,
    "name": 0x01010003,
    "permission": 0x01010006,
    "exported": 0x01010010,
    "versionCode": 0x0101021b,
    "versionName": 0x0101021c,
    "minSdkVersion": 0x0101020c,
    "targetSdkVersion": 0x01010270,
    "allowBackup": 0x01010280,
    "debuggable": 0x0101000f,
    "enabled": 0x0101000e,
}

DOC = []


def S(name, attrs=None):
    DOC.append(("start", name, attrs or []))


def E(name):
    DOC.append(("end", name, None))


S("manifest", [
    (None, "package", "str", "io.github.sakurainakeipoi.hapticboost"),
    ("android", "versionCode", "int", 2),
    ("android", "versionName", "str", "1.1"),
])
S("uses-sdk", [
    ("android", "minSdkVersion", "int", 21),
    ("android", "targetSdkVersion", "int", 29),
])
E("uses-sdk")
S("uses-permission", [("android", "name", "str", "android.permission.VIBRATE")])
E("uses-permission")
S("application", [
    ("android", "label", "str", "震动增强"),
    ("android", "allowBackup", "bool", False),
])
S("activity", [
    ("android", "name", "str", "io.github.sakurainakeipoi.hapticboost.MainActivity"),
    ("android", "label", "str", "震动增强"),
    ("android", "exported", "bool", True),
])
S("intent-filter", [])
S("action", [("android", "name", "str", "android.intent.action.MAIN")])
E("action")
S("category", [("android", "name", "str", "android.intent.category.LAUNCHER")])
E("category")
E("intent-filter")
E("activity")
E("application")
E("manifest")

# ---- 1) 属性名（有资源ID的）排在最前 ----
attr_names = []
for item in DOC:
    if item[0] == "start":
        for (ns, nm, kind, val) in item[2]:
            if nm in ATTR_ID and ns is not None and nm not in attr_names:
                attr_names.append(nm)

pool = list(attr_names)


def sid(s):
    if s not in pool:
        pool.append(s)
    return pool.index(s)


# ---- 2) 登记其余字符串 ----
sid(NS_PREFIX)
sid(NS_URI)
for item in DOC:
    if item[0] == "start":
        sid(item[1])
        for (ns, nm, kind, val) in item[2]:
            sid(nm)
            if kind == "str":
                sid(val)

# ---- 3) UTF-16 字符串池 ----
data = b""
offsets = []
for s in pool:
    offsets.append(len(data))
    data += struct.pack("<H", len(s)) + s.encode("utf-16-le") + b"\x00\x00"
while len(data) % 4:
    data += b"\x00"

strings_start = 28 + 4 * len(pool)
sp_size = strings_start + len(data)
pool_chunk = struct.pack("<HHI", 0x0001, 28, sp_size)
pool_chunk += struct.pack("<IIIII", len(pool), 0, 0x00000000, strings_start, 0)
for o in offsets:
    pool_chunk += struct.pack("<I", o)
pool_chunk += data

# ---- 4) 资源映射表：只覆盖属性名 ----
rm_payload = b"".join(struct.pack("<I", ATTR_ID[n]) for n in attr_names)
resmap_chunk = struct.pack("<HHI", 0x0180, 8, 8 + len(rm_payload)) + rm_payload

# ---- 5) XML 节点 ----
nodes = b""


def node(ntype, payload):
    # payload 为 8 字节 chunk 头之后的所有内容
    return struct.pack("<HHI", ntype, 16, 8 + len(payload)) + payload


ns_payload = struct.pack("<IIII", 0xFFFFFFFF, 0xFFFFFFFF, sid(NS_PREFIX), sid(NS_URI))
nodes += node(0x0100, ns_payload)

for item in DOC:
    if item[0] == "start":
        _, name, attrs = item
        blob = b""
        for (ns, aname, kind, val) in attrs:
            ns_idx = 0xFFFFFFFF if ns is None else (sid(NS_URI) if ns == NS_PREFIX else sid(ns))
            if kind == "str":
                raw_idx, dtype, dval = sid(val), 0x03, sid(val)
            elif kind == "int":
                raw_idx, dtype, dval = 0xFFFFFFFF, 0x10, val
            elif kind == "bool":
                raw_idx, dtype, dval = 0xFFFFFFFF, 0x12, (1 if val else 0)
            else:
                raise ValueError(kind)
            blob += struct.pack("<II", ns_idx, sid(aname))
            blob += struct.pack("<I", raw_idx)
            blob += struct.pack("<HBBI", 8, 0, dtype, dval)
        ext = struct.pack("<II", 0xFFFFFFFF, sid(name))
        ext += struct.pack("<HHHHHH", 20, 20, len(attrs), 0, 0, 0)
        payload = struct.pack("<II", 0xFFFFFFFF, 0xFFFFFFFF) + ext + blob
        nodes += node(0x0102, payload)
    else:
        payload = struct.pack("<IIII", 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, sid(item[1]))
        nodes += node(0x0103, payload)

nodes += node(0x0101, ns_payload)

total = 8 + len(pool_chunk) + len(resmap_chunk) + len(nodes)
xml = struct.pack("<HHI", 0x0003, 8, total) + pool_chunk + resmap_chunk + nodes

out = sys.argv[1] if len(sys.argv) > 1 else "AndroidManifest.xml"
open(out, "wb").write(xml)
print("wrote %s (%d bytes, %d strings, %d attr-mapped)" % (out, len(xml), len(pool), len(attr_names)))
