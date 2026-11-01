"""Authorized Samsung QA. Every tap resolves an exact label in a fresh UI tree."""
import argparse
import datetime
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ADB = r'C:\Users\slavd\AppData\Local\Android\Sdk\platform-tools\adb.exe'
SERIAL = 'R5CTA0TKLZZ'
OUT = Path(__file__).resolve().parent / 'phone'
OUT.mkdir(exist_ok=True)

def adb(*args):
    return subprocess.check_output([ADB, '-s', SERIAL, *args], timeout=45)

def tree(name):
    raw = adb('exec-out', 'uiautomator', 'dump', '/dev/tty').decode('utf-8')
    xml = raw[raw.index('<?xml'):raw.rindex('</hierarchy>') + len('</hierarchy>')]
    (OUT / (name + '.xml')).write_text(xml, encoding='utf-8')
    return ET.fromstring(xml)

def bounds(node):
    return list(map(int, re.findall(r'\d+', node.attrib['bounds'])))

def summary(root):
    for node in root.iter('node'):
        a = node.attrib
        label = a.get('text') or a.get('content-desc')
        if label or a.get('scrollable') == 'true' or a.get('class') == 'android.widget.EditText':
            print(json.dumps({k: a.get(k) for k in ('text', 'content-desc', 'class', 'bounds', 'clickable', 'enabled', 'scrollable')}, ensure_ascii=False))

p = argparse.ArgumentParser()
p.add_argument('command', choices=['snapshot', 'tap', 'text', 'back', 'scroll'])
p.add_argument('name')
p.add_argument('value', nargs='?')
p.add_argument('--index', type=int, default=0)
a = p.parse_args()
if a.command == 'snapshot':
    root = tree(a.name)
    (OUT / (a.name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    summary(root)
elif a.command == 'tap':
    root = tree(a.name + '-before')
    nodes = [n for n in root.iter('node') if a.value in (n.get('text'), n.get('content-desc'))]
    assert nodes and a.index < len(nodes), 'Label missing; inspect/scroll/re-dump before continuing'
    n = nodes[a.index]
    assert n.get('enabled') == 'true', 'Target is disabled'
    x1, y1, x2, y2 = bounds(n)
    assert x2 > x1 and y2 > y1
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
elif a.command == 'text':
    assert a.value and a.value.isascii()
    adb('shell', 'input', 'text', a.value.replace(' ', '%s'))
elif a.command == 'back':
    adb('shell', 'input', 'keyevent', '4')
elif a.command == 'scroll':
    root = tree(a.name + '-before')
    nodes = [n for n in root.iter('node') if n.get('scrollable') == 'true']
    assert nodes and a.index < len(nodes), 'No scrollable node'
    x1, y1, x2, y2 = bounds(nodes[a.index])
    adb('shell', 'input', 'swipe', str((x1+x2)//2), str(y1+(y2-y1)*3//4), str((x1+x2)//2), str(y1+(y2-y1)//4), '450')
with (OUT / 'actions.jsonl').open('a', encoding='utf-8') as f:
    f.write(json.dumps({'utc': datetime.datetime.now(datetime.timezone.utc).isoformat(), **vars(a)}, ensure_ascii=False)+'\n')
