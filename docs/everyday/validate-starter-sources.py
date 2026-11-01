"""Explicit host-only endpoint validation. Never run as app startup or as a Local-mode collector.

Retains response metadata and a hash, not publishers' article bodies. App transport implements
its own policy and URL/DNS safety; this script tests the curated public endpoints below.
"""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
import hashlib
import json
import urllib.request
import xml.etree.ElementTree as ET

SOURCES = [
    ("lsm-lv", "lv", "https://www.lsm.lv/rss/?lang=lv&catid=14", "https://www.lsm.lv/barotnes/rss/"),
    ("lsm-ru", "ru", "https://rus.lsm.lv/rss/", "https://rus.lsm.lv/"),
    ("lsm-en-politics", "en", "https://eng.lsm.lv/rss/?lang=en&catid=315", "https://eng.lsm.lv/feeds/rss/"),
    ("lsm-en-society", "en", "https://eng.lsm.lv/rss/?lang=en&catid=278", "https://eng.lsm.lv/feeds/rss/"),
    ("bbc-world", "en", "https://feeds.bbci.co.uk/news/world/rss.xml", "https://www.bbc.com/news"),
    ("nasa", "en", "https://www.nasa.gov/feed/", "https://www.nasa.gov/rss-feeds/"),
    ("ars", "en", "https://feeds.arstechnica.com/arstechnica/index", "https://arstechnica.com/"),
    ("verge", "en", "https://www.theverge.com/rss/index.xml", "https://www.theverge.com/"),
    ("hacker-news", "en", "https://news.ycombinator.com/rss", "https://news.ycombinator.com/"),
    ("cert-lv", "lv", "https://cert.lv/lv/rss/incidenti/bridinajumi.xml", "https://cert.lv/"),
    ("open-meteo", "en", "https://api.open-meteo.com/v1/forecast?latitude=56.95&longitude=24.11&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=Europe%2FRiga&forecast_days=3", "https://open-meteo.com/en/docs"),
]

def check(source):
    key, language, url, documentation = source
    result = dict(id=key, language=language, requested_url=url, documentation=documentation,
                  checked_utc=datetime.now(timezone.utc).isoformat(), valid=False)
    try:
        request = urllib.request.Request(url, headers={"User-Agent": "Halo-source-validation/1.0", "Accept": "application/rss+xml, application/atom+xml, application/json, text/xml"})
        with urllib.request.urlopen(request, timeout=25) as response:
            body = response.read(2 * 1024 * 1024 + 1)
            result.update(status=response.status, final_url=response.url, content_type=response.headers.get("Content-Type"),
                          bytes=len(body), etag=response.headers.get("ETag"), last_modified=response.headers.get("Last-Modified"))
        if len(body) > 2 * 1024 * 1024:
            raise ValueError("Response exceeds app collector size budget")
        result["sha256"] = hashlib.sha256(body).hexdigest()
        if key == "open-meteo":
            value = json.loads(body)
            result.update(valid=isinstance(value.get("current", {}).get("temperature_2m"), (int, float)) and len(value.get("daily", {}).get("time", [])) > 0,
                          daily_count=len(value.get("daily", {}).get("time", [])), timezone=value.get("timezone"))
        else:
            if b"<!DOCTYPE" in body.upper() or b"<!ENTITY" in body.upper():
                raise ValueError("DTD/entity declarations are not accepted")
            root = ET.fromstring(body)
            tag = root.tag.split("}")[-1]
            entries = list(root.findall("./channel/item")) if tag == "rss" else list(root.findall("{http://www.w3.org/2005/Atom}entry"))
            result.update(format=tag, item_count=len(entries), valid=tag in ("rss", "feed") and bool(entries))
    except Exception as failure:
        result["error"] = f"{type(failure).__name__}: {failure}"
    return result

if __name__ == "__main__":
    with ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(check, SOURCES))
    output = Path(__file__).with_name("starter-source-validation.json")
    output.write_text(json.dumps({"scope": "Host endpoint availability and structure; not app/runtime/phone acceptance or a content license", "sources": results}, indent=2) + "\n", encoding="utf-8")
    for result in results:
        print(result["id"], "VALID" if result["valid"] else "UNAVAILABLE", result.get("status"), result.get("item_count", result.get("daily_count")), result.get("error", ""))
