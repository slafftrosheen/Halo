"""Author Android vectors from DevKit v1.2 static FX, retaining supplied geometry.

No screenshots or raster intermediates. SVG groups use unskewed matrices; group
opacity is inherited by each path and gradient stop alpha remains independent.
Run after changing the supplied SVGs, then review the generated resource diff.
"""
from pathlib import Path
import math
import re
import copy
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "DevKit_v1.2/fx/static"
OUTPUT = ROOT / "app/src/main/res/drawable"
KOTLIN = ROOT / "app/src/main/java/com/halo/app/ui/fx/HaloStaticFxAssets.kt"
ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
ET.register_namespace("android", ANDROID)
ET.register_namespace("aapt", AAPT)
ACCENTS = {
    "mint": ("0CF2BA", "B4E6D6", "047A5E"),
    "blue": ("6FB4FF", "C9E3FF", "1F5FBF"),
    "lilac": ("A98EFF", "E2D9FF", "6446D6"),
    "coral": ("FF7E9C", "FFD0DB", "B8325A"),
}
MOMENTS = ("splash", "summon", "bloom", "bloom_kids", "remember",
           "cloud_handoff", "draft_drawon", "canvas_arrange", "device_connect",
           "tool_timer", "tool_search", "tool_open_app", "tool_camera",
           "tool_calendar", "tool_send")
FIXED_BRAND = {"splash", "bloom_kids"}


def attr(name):
    return f"{{{ANDROID}}}{name}"


def number(value):
    return f"{float(value):.9g}"


def dashed_length(data):
    """Arc length for the supplied M/C/z paths; vector trim preserves the curves."""
    tokens = re.findall(r"[MCz]|[-+]?(?:\d*\.)?\d+(?:[eE][-+]?\d+)?", data)
    index = 0
    current = start = (0., 0.)
    length = 0.
    while index < len(tokens):
        command = tokens[index]
        index += 1
        if command == "M":
            current = start = tuple(map(float, tokens[index:index + 2]))
            index += 2
        elif command == "C":
            values = list(map(float, tokens[index:index + 6]))
            index += 6
            p1, p2, end = values[:2], values[2:4], values[4:]
            prior = current
            for step in range(1, 1025):
                t = step / 1024
                u = 1 - t
                point = tuple(u**3 * current[k] + 3 * u*u*t * p1[k] +
                              3 * u*t*t * p2[k] + t**3 * end[k] for k in (0, 1))
                length += math.dist(prior, point)
                prior = point
            current = tuple(end)
        elif command == "z":
            length += math.dist(current, start)
            current = start
        else:
            raise ValueError(f"Unsupported dashed path command: {command}")
    return length


def color(value, accent, alpha=1):
    if value.startswith("rgb("):
        channels = [int(x) for x in re.findall(r"\d+", value)]
        rgb = "".join(f"{x:02X}" for x in channels)
    elif value.startswith("#"):
        rgb = value[1:].upper()
    else:
        raise ValueError(f"Unsupported SVG color: {value}")
    replacements = dict(zip(ACCENTS["mint"], ACCENTS[accent]))
    rgb = replacements.get(rgb, rgb)
    return f"#{round(float(alpha) * 255):02X}{rgb}"


def paint(path, svg, key, opacity, gradients, accent):
    value = svg.get(key, "black" if key == "fill" else "none")
    if value == "none" or (key == "fill" and value == "black" and svg.get("fill-opacity") == "0"):
        path.set(attr(key + "Color"), "#00000000")
        return
    path.set(attr(key + "Alpha"), number(opacity * float(svg.get(key + "-opacity", 1))))
    if not value.startswith("url("):
        path.set(attr(key + "Color"), color(value, accent))
        return
    definition = gradients[value.removeprefix("url(#").removesuffix(")")]
    holder = ET.SubElement(path, f"{{{AAPT}}}attr", {"name": "android:" + key + "Color"})
    gradient = ET.SubElement(holder, "gradient")
    radial = definition.tag.endswith("radialGradient")
    gradient.set(attr("type"), "radial" if radial else "linear")
    coordinates = {"cx": "centerX", "cy": "centerY", "r": "gradientRadius"} if radial else {
        "x1": "startX", "y1": "startY", "x2": "endX", "y2": "endY"}
    for source, target in coordinates.items():
        gradient.set(attr(target), definition.get(source, "0"))
    for stop in definition:
        offset = stop.get("offset", "0")
        fraction = float(offset.rstrip("%")) / (100 if offset.endswith("%") else 1)
        ET.SubElement(gradient, "item", {attr("offset"): number(fraction),
            attr("color"): color(stop.get("stop-color"), accent, stop.get("stop-opacity", 1))})


def emit(parent, svg, opacity, gradients, accent):
    tag = svg.tag.rsplit("}", 1)[-1]
    if tag in {"defs", "clipPath"} or "display: none" in svg.get("style", ""):
        return
    opacity *= float(svg.get("opacity", 1))
    target = parent
    if tag == "g":
        target = ET.SubElement(parent, "group")
        if transform := svg.get("transform"):
            a, b, c, d, tx, ty = map(float, re.findall(r"[-+]?(?:\d*\.)?\d+(?:[eE][-+]?\d+)?", transform))
            if abs(a * c + b * d) > .0001:
                raise ValueError("Skew requires an explicit path transform")
            sx = math.hypot(a, b)
            sy = (a * d - b * c) / sx if sx else math.hypot(c, d)
            for name, value in {"translateX": tx, "translateY": ty, "scaleX": sx,
                                "scaleY": sy, "rotation": math.degrees(math.atan2(b, a))}.items():
                target.set(attr(name), number(value))
    elif tag == "path":
        target = ET.SubElement(parent, "path", {attr("pathData"): svg.get("d")})
        for key in ("fill", "stroke"):
            paint(target, svg, key, opacity, gradients, accent)
        for source, dest in {"stroke-width": "strokeWidth", "stroke-linecap": "strokeLineCap",
                             "stroke-linejoin": "strokeLineJoin"}.items():
            if value := svg.get(source):
                target.set(attr(dest), value)
        if pattern := svg.get("stroke-dasharray"):
            if float(svg.get("stroke-dashoffset", "0")) != 0:
                raise ValueError("Nonzero dash offset needs an explicit conversion")
            dash, gap = map(float, pattern.split())
            length = dashed_length(svg.get("d"))
            parent.remove(target)
            offset = 0.
            while offset < length:
                segment = copy.deepcopy(target)
                segment.set(attr("trimPathStart"), number(offset / length))
                segment.set(attr("trimPathEnd"), number(min(offset + dash, length) / length))
                parent.append(segment)
                offset += dash + gap
        return
    for child in svg:
        emit(target, child, opacity, gradients, accent)


def generate():
    resources = []
    for moment in MOMENTS:
        for mode in ("dark", "light"):
            svg = ET.parse(SOURCE / f"halo_fx_{moment}_{mode}.svg").getroot()
            _, _, width, height = svg.get("viewBox").split()
            gradients = {node.get("id"): node for node in svg.iter()
                         if node.tag.endswith(("linearGradient", "radialGradient"))}
            for accent in (("mint",) if moment in FIXED_BRAND else ACCENTS):
                name = f"halo_fx_static_{moment}_{mode}_{accent}"
                vector = ET.Element("vector", {attr("width"): width + "dp", attr("height"): height + "dp",
                    attr("viewportWidth"): width, attr("viewportHeight"): height})
                emit(vector, svg, 1, gradients, accent)
                ET.indent(vector, space="  ")
                (OUTPUT / (name + ".xml")).write_text(
                    '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(vector, encoding="unicode") + "\n", encoding="utf-8")
                resources.append((moment, mode, accent, name))
    lines = ["package com.halo.app.ui.fx", "", "import com.halo.app.R", "import com.halo.app.ui.theme.HaloAccent", "",
             "// Generated by scripts/brand/generate_static_fx.py from the canonical static SVGs.",
             "internal fun haloStaticFxResource(asset: String, dark: Boolean, accent: HaloAccent): Int {",
             '    val mode = if (dark) "dark" else "light"',
             '    val palette = if (asset in setOf("splash", "bloom_kids")) "mint" else accent.name.lowercase()',
             '    return when ("${asset}_${mode}_$palette") {']
    for moment, mode, accent, name in resources:
        lines.append(f'        "{moment}_{mode}_{accent}" -> R.drawable.{name}')
    lines += ['        else -> error("Unknown static halo effect: $asset")', "    }", "}", ""]
    KOTLIN.write_text("\n".join(lines), encoding="utf-8")
    print(f"Generated {len(resources)} static FX vectors and resource mapping")


if __name__ == "__main__":
    generate()
