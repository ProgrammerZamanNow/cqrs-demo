#!/usr/bin/env python3
"""Benchmark 3 engine baca × 2 mode (search murni / + facet).

  PG naif     : /api/products                     (LIKE '%..%' seq scan)
  PG+trigram  : /api/products?engine=trigram       (GIN pg_trgm)
  OpenSearch  : /api/products/_search              (n-gram inverted index)

Mode dipisah lewat param facet=true/false → mengisolasi biaya SEARCH murni dari
biaya agregasi FACET. Angka yang dilaporkan = metadata.processTimeMs (server-side).

Catatan: OpenSearch butuh warmup lebih (page cache + JIT + query cache), jadi ada
PASS WARMUP KHUSUS OpenSearch sebelum warmup umum & pengukuran.
"""
import json
import urllib.request
import urllib.parse

BASE_PG = "http://localhost:8080/api/products"
BASE_OS = "http://localhost:8081/api/products/_search"

# (label, base_url, query tetap) — keyword/size/facet ditambahkan saat runtime
ENGINES = [
    ("PG naif", BASE_PG, {}),
    ("PG+trigram", BASE_PG, {"engine": "trigram"}),
    ("OpenSearch", BASE_OS, {}),
]
# keyword dengan selektivitas berbeda (selektif → umum)
KEYWORDS = ["titanium", "handcrafted", "rubber", "ergonomic"]

OS_WARMUP_ROUNDS = 6   # warmup KHUSUS OpenSearch (cache dingin)
WARMUP_ROUNDS = 3      # warmup umum tiap URL sebelum diukur
RUNS = 5               # jumlah pengukuran


def url_for(base, fixed, kw, facet):
    q = dict(fixed)
    q["keyword"] = kw
    q["size"] = "10"
    q["facet"] = "true" if facet else "false"
    return base + "?" + urllib.parse.urlencode(q)


def hit(url):
    with urllib.request.urlopen(url, timeout=180) as r:
        d = json.load(r)
    return d["metadata"]["processTimeMs"], d["paging"]["totalElement"]


def warm(url, rounds):
    for _ in range(rounds):
        hit(url)


def measure(url):
    srv = []
    total = None
    for _ in range(RUNS):
        s, total = hit(url)
        srv.append(s)
    return min(srv), sum(srv) / len(srv), max(srv), total


def all_urls(facet):
    for label, base, fixed in ENGINES:
        for kw in KEYWORDS:
            yield label, kw, url_for(base, fixed, kw, facet)


def main():
    # 1) WARMUP KHUSUS OpenSearch — hajar semua URL OS dulu (kedua mode facet).
    print(f"Warmup OpenSearch ({OS_WARMUP_ROUNDS}x semua keyword × mode)...")
    for facet in (False, True):
        for label, base, fixed in ENGINES:
            if label != "OpenSearch":
                continue
            for kw in KEYWORDS:
                warm(url_for(base, fixed, kw, facet), OS_WARMUP_ROUNDS)

    # 2) Warmup umum + ukur, per mode facet.
    results = {}  # (mode, label, kw) -> (min, avg, max, total)
    totals = {}   # kw -> matches
    for facet in (False, True):
        mode = "facet" if facet else "search"
        print(f"Warmup umum + ukur (mode: {'+facet' if facet else 'search murni'})...")
        for label, kw, url in all_urls(facet):
            warm(url, WARMUP_ROUNDS)
            mn, avg, mx, total = measure(url)
            results[(mode, label, kw)] = (mn, avg, mx)
            totals[kw] = total

    # 3) Cetak tabel per mode.
    labels = [e[0] for e in ENGINES]
    for facet in (False, True):
        mode = "facet" if facet else "search"
        title = "SEARCH MURNI (facet=false)" if not facet else "SEARCH + FACET (facet=true)"
        print("\n" + "=" * 78)
        print(f"  {title}   — processTimeMs (min / avg)")
        print("=" * 78)
        header = f"{'keyword':<14}{'matches':>10}  "
        header += "".join(f"{l:>18}" for l in labels)
        print(header)
        print("-" * 78)
        for kw in KEYWORDS:
            line = f"{kw:<14}{totals[kw]:>10}  "
            for label in labels:
                mn, avg, mx = results[(mode, label, kw)]
                line += f"{f'{mn}/{avg:.0f}ms':>18}"
            print(line)

    # 4) Ringkasan rata-rata + speedup (mode search murni).
    print("\n" + "=" * 78)
    print("  RINGKASAN — rata-rata processTimeMs (avg) lintas keyword")
    print("=" * 78)
    avgs = {}
    for facet in (False, True):
        mode = "facet" if facet else "search"
        for label in labels:
            vals = [results[(mode, label, kw)][1] for kw in KEYWORDS]
            avgs[(mode, label)] = sum(vals) / len(vals)
    print(f"{'engine':<14}{'search murni':>16}{'+ facet':>14}")
    print("-" * 78)
    for label in labels:
        print(f"{label:<14}{avgs[('search', label)]:>13.0f} ms{avgs[('facet', label)]:>11.0f} ms")

    base = avgs[("search", "PG naif")]
    print("\nSpeedup search murni vs PG naif:")
    for label in labels:
        a = avgs[("search", label)]
        print(f"  {label:<14} ~{base / a:5.1f}x")


if __name__ == "__main__":
    main()
