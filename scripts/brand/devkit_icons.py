"""Pure conversion and strict structural validation of tintable DevKit icons.

Only XML formatting, numeric viewport spelling and explicit default paint values
are normalized; geometry, colors and every other attribute remain significant.
"""
import xml.etree.ElementTree as ET

ANDROID = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID)
def attr(name):
    return "{" + ANDROID + "}" + name


def convert_svg_to_vector(source: str) -> str:
    svg = ET.fromstring(source)
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
    return ET.tostring(vector, encoding="unicode") + "\n"


def vector_signature(source: str):
    """Ignore serialization details and documented Android paint defaults only."""
    def signature(element):
        attributes = dict(element.attrib)
        for name in ("viewportWidth", "viewportHeight"):
            key = attr(name)
            if key in attributes:
                attributes[key] = str(float(attributes[key]))
        for name in ("fillAlpha", "strokeAlpha"):
            key = attr(name)
            if key in attributes and float(attributes[key]) == 1:
                del attributes[key]
        if element.tag == "path":
            for name in ("fillColor", "strokeColor"):
                key = attr(name)
                if attributes.get(key) == "#00000000":
                    del attributes[key]
        return (element.tag, tuple(sorted(attributes.items())),
                (element.text or "").strip(), (element.tail or "").strip(),
                tuple(signature(child) for child in element))
    return signature(ET.fromstring(source))
