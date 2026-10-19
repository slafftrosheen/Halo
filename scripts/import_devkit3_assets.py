"""Vendor the supplied runtime artwork without shipping reference boards or films."""
from pathlib import Path
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
KIT = ROOT / "docs/15-brand/v4"
PRODUCT = KIT / "product"
ASSETS = ROOT / "app/src/main/assets/halo"
receipts = []


def copy(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)
    receipts.append({
        "source": source.relative_to(ROOT).as_posix(),
        "asset": destination.relative_to(ASSETS).as_posix(),
        "sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
    })


for source in sorted((PRODUCT / "B/source/a/lottie").glob("*.json")):
    copy(source, ASSETS / ("fx" if source.name.startswith("halo_fx_") else "") / source.name)
for source in sorted((PRODUCT / "B/source/a/fx").glob("*.svg")):
    copy(source, ASSETS / "fx/static" / source.name)
for package in ("B", "C", "D", "E", "F", "G", "SCREEN"):
    for source in sorted((PRODUCT / package / "lottie").glob("*.json")):
        copy(source, ASSETS / "fx" / source.name)
    for source in sorted((PRODUCT / package / "lottie/static").glob("*.svg")):
        copy(source, ASSETS / "fx/static" / source.name)
for folder in ("ic", "orb", "wm", "vr"):
    for source in sorted((PRODUCT / "B/source/a" / folder).glob("*.svg")):
        copy(source, ASSETS / "artwork" / folder / source.name)

for folder in ("fx", "learning", "voice"):
    for source in sorted((KIT / folder / "lottie").glob("*.json")):
        copy(source, ASSETS / "fx" / source.name)
    for source in sorted((KIT / folder / "static").glob("*.svg")):
        copy(source, ASSETS / "fx/static" / source.name)
for source in sorted((KIT / "orb/lottie").glob("*.json")):
    copy(source, ASSETS / source.name)
for source in sorted((KIT / "kids/lottie").glob("*.json")):
    copy(source, ASSETS / source.name)
for folder, target in (("icons/svg", "ic"), ("orb/svg", "orb"), ("orb/micro", "micro"), ("kids/orb_svg", "kids"), ("wordmark", "wm")):
    for source in sorted((KIT / folder).glob("*.svg")):
        copy(source, ASSETS / "artwork" / target / source.name)

# Icons are native VectorDrawables: no network, asynchronous SVG decode or substitute glyphs.
ANDROID = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID)
def attr(name):
    return "{" + ANDROID + "}" + name

for source in sorted((KIT / "icons/svg").glob("*.svg")):
    svg = ET.parse(source).getroot()
    view = [float(v) for v in svg.get("viewBox", "0 0 24 24").split()]
    vector = ET.Element("vector", {attr("width"): "24dp", attr("height"): "24dp",
        attr("viewportWidth"): str(view[2]), attr("viewportHeight"): str(view[3])})
    def walk(node, inherited):
        style = {**inherited, **node.attrib}
        tag = node.tag.split("}")[-1]
        path = node.get("d") if tag == "path" else None
        if tag in ("circle", "ellipse"):
            x, y = float(node.get("cx", 0)), float(node.get("cy", 0))
            rx = float(node.get("r", node.get("rx", 0))); ry = float(node.get("r", node.get("ry", 0)))
            path = f"M{x-rx},{y}a{rx},{ry} 0 1,0 {2*rx},0a{rx},{ry} 0 1,0 {-2*rx},0"
        elif tag == "line":
            path = f"M{node.get('x1',0)},{node.get('y1',0)}L{node.get('x2',0)},{node.get('y2',0)}"
        elif tag in ("polyline", "polygon"):
            path = "M" + node.get("points", "") + ("Z" if tag == "polygon" else "")
        elif tag == "rect":
            x, y, w, h = [float(node.get(k, 0)) for k in ("x", "y", "width", "height")]
            r = min(float(node.get("rx", 0)), w/2, h/2)
            path = (f"M{x+r},{y}H{x+w-r}A{r},{r} 0 0,1 {x+w},{y+r}V{y+h-r}A{r},{r} 0 0,1 {x+w-r},{y+h}H{x+r}A{r},{r} 0 0,1 {x},{y+h-r}V{y+r}A{r},{r} 0 0,1 {x+r},{y}Z"
                if r else f"M{x},{y}H{x+w}V{y+h}H{x}Z")
        if path:
            attributes = {attr("pathData"): path}
            for key, target, default in (("fill", "fillColor", "none"), ("stroke", "strokeColor", "none")):
                color = style.get(key, default)
                attributes[attr(target)] = "#00000000" if color == "none" else "#FFFFFFFF" if color == "currentColor" else color
            for key, target in (("stroke-width", "strokeWidth"), ("stroke-linecap", "strokeLineCap"), ("stroke-linejoin", "strokeLineJoin")):
                if key in style: attributes[attr(target)] = style[key]
            for key, target in (("fill-opacity", "fillAlpha"), ("stroke-opacity", "strokeAlpha")):
                attributes[attr(target)] = str(float(style.get(key, 1)) * float(style.get("opacity", 1)))
            if style.get("fill-rule") == "evenodd": attributes[attr("fillType")] = "evenOdd"
            ET.SubElement(vector, "path", attributes)
        for child in node:
            walk(child, style)
    walk(svg, {})
    ET.indent(vector)
    (ROOT / "app/src/main/res/drawable" / (source.stem + ".xml")).write_text(
        ET.tostring(vector, encoding="unicode") + "\n", encoding="utf-8")
(ASSETS / "artwork/manifest.json").write_text(
    json.dumps({"source": "Brand Book 3.0 / DevKit 4.0", "files": receipts}, indent=2) + "\n",
    encoding="utf-8",
)
print(f"Imported {len(receipts)} supplied runtime assets")
