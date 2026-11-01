"""Build Halo's offline city asset from the attributed GeoNames extract. Host build-time only."""
from pathlib import Path
import datetime
import gzip
import hashlib
import io
import json
import urllib.request
import zipfile

source = "https://download.geonames.org/export/dump/cities15000.zip"
root = Path(__file__).resolve().parents[2] / "app/src/main/assets/everyday"
root.mkdir(parents=True, exist_ok=True)
request = urllib.request.Request(source, headers={"User-Agent": "Halo-offline-catalogue-builder/1.0"})
with urllib.request.urlopen(request, timeout=45) as response:
    raw = response.read(20 * 1024 * 1024 + 1)
    assert len(raw) <= 20 * 1024 * 1024
    modified = response.headers.get("Last-Modified")
with zipfile.ZipFile(io.BytesIO(raw)) as archive:
    info = archive.getinfo("cities15000.txt")
    assert info.file_size <= 64 * 1024 * 1024
    text = archive.read(info).decode("utf-8")
rows = []
for line in text.splitlines():
    columns = line.split("\t")
    if len(columns) != 19:
        raise ValueError("Unexpected GeoNames schema")
    identity, name, ascii_name, aliases = columns[:4]
    latitude, longitude, country, population, zone = columns[4], columns[5], columns[8], columns[14], columns[17]
    if not zone or not country or not name:
        continue
    assert -90 <= float(latitude) <= 90 and -180 <= float(longitude) <= 180
    # Keep identifiers/time zones exact; bound display/search strings without guessing localized names.
    rows.append([identity, name[:120], ascii_name[:120], country, latitude, longitude, zone, population, aliases[:2000]])
rows.sort(key=lambda row: (-int(row[7]), int(row[0])))
assert 10_000 <= len(rows) <= 50_000
payload = ("\n".join("\t".join(row) for row in rows) + "\n").encode("utf-8")
assert len(payload) <= 16 * 1024 * 1024
compressed = gzip.compress(payload, compresslevel=9, mtime=0)
(root / "everyday-cities.bin").write_bytes(compressed)
metadata = {
    "assetFile": "everyday-cities.bin",
    "assetEncoding": "gzip-compressed UTF-8 TSV; .bin prevents Android asset merging from expanding .gz",
    "source": source,
    "documentation": "https://download.geonames.org/export/dump/readme.txt",
    "attribution": "City data: GeoNames, licensed under CC BY 4.0. Halo selects columns, bounds labels/aliases and compresses this extract.",
    "attributionUrl": "https://www.geonames.org/",
    "license": "https://creativecommons.org/licenses/by/4.0/",
    "retrievedUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    "sourceLastModified": modified,
    "sourceZipSha256": hashlib.sha256(raw).hexdigest(),
    "assetSha256": hashlib.sha256(compressed).hexdigest(),
    "rows": len(rows), "uncompressedBytes": len(payload), "assetBytes": len(compressed),
    "columns": ["geonameid", "name", "asciiName", "countryCode", "latitude", "longitude", "timeZone", "population", "aliases"],
}
(root / "everyday-cities-provenance.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
print(json.dumps({key: metadata[key] for key in ["rows", "uncompressedBytes", "assetBytes", "assetSha256"]}))
