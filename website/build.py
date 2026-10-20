#!/usr/bin/env python3
"""Build and check the MOSIP Registration Client website / in-browser demo.

    python3 website/build.py          # writes website/index.html from index.src.html + spec.json
    python3 website/build.py --check  # fails if index.html is stale or the page breaks a project rule

index.html = HEADER + index.src.html (with __SPEC_JSON__ replaced by spec.json) + FOOTER.
spec.json is the compact UI spec (see docs/discovery.md, section b3) used by the demo client.
"""
import json
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
HEADER = (
    "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
    "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\">\n"
    "<style>[hidden]{display:none!important}body{margin:0}img{max-width:100%}</style>\n"
    "</head>\n<body>\n"
)
FOOTER = "\n</body>\n</html>\n"


def build() -> str:
    src = (HERE / "index.src.html").read_text(encoding="utf-8")
    spec = json.loads((HERE / "spec.json").read_text(encoding="utf-8"))
    if src.count("__SPEC_JSON__") != 1:
        raise SystemExit("index.src.html must contain __SPEC_JSON__ exactly once")
    body = src.replace("__SPEC_JSON__", json.dumps(spec, separators=(",", ":"), ensure_ascii=False))
    return HEADER + body + FOOTER


def rule_violations(html: str) -> list:
    """Project rules: offline-first (no CDN code or fonts) and no dynamic code evaluation."""
    problems = []
    for m in re.finditer(r"<(script|link)\b[^>]*\b(src|href)=\"(https?:)?//[^\"]+\"[^>]*>", html):
        if m.group(1) == "script" or "stylesheet" in m.group(0) or "preconnect" in m.group(0):
            problems.append("external resource: " + m.group(0)[:120])
    for pattern in (r"\bnew Function\b", r"\beval\("):
        if re.search(pattern, html):
            problems.append("dynamic code evaluation: " + pattern)
    return problems


def main() -> int:
    html = build()
    out = HERE / "index.html"
    if "--check" in sys.argv:
        ok = True
        if out.read_text(encoding="utf-8") != html:
            print("index.html is stale: run python3 website/build.py")
            ok = False
        for p in rule_violations(html):
            print("rule violation:", p)
            ok = False
        print("website check:", "OK" if ok else "FAILED")
        return 0 if ok else 1
    out.write_text(html, encoding="utf-8")
    print("wrote", out.relative_to(HERE.parent), len(html), "bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
