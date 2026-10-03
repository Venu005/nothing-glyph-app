"""Builds app/src/main/assets/cities.tsv from GeoNames cities15000.txt (CC BY 4.0).

Usage: python3 scripts/build_cities.py /path/to/cities15000.txt
Keeps cities with population >= 300,000. Output columns: name, country code, lat, lon.
"""
import sys

MIN_POP = 300_000
src = sys.argv[1]
rows = []
with open(src, encoding="utf-8") as f:
    for line in f:
        p = line.rstrip("\n").split("\t")
        name, lat, lon, country, pop = p[1], float(p[4]), float(p[5]), p[8], int(p[14] or 0)
        if pop >= MIN_POP:
            rows.append((pop, name, country, round(lat, 4), round(lon, 4)))
rows.sort(reverse=True)
with open("app/src/main/assets/cities.tsv", "w", encoding="utf-8") as out:
    for _, name, country, lat, lon in rows:
        out.write(f"{name}\t{country}\t{lat}\t{lon}\n")
print(f"wrote {len(rows)} cities")
