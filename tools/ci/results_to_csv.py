#!/usr/bin/env python3
"""Convert automated test reports into one CSV (standard library only).

Inputs
  --junit SUITE:MODULE:DIR     a folder of JUnit XML files (Gradle build/test-results or Maven surefire-reports)
  --flutter SUITE:MODULE:FILE  the output of `flutter test --reporter json`
Output columns
  run_date, commit, suite, module, test_class, test_name, status, duration_ms, failure_message
  status is PASSED, FAILED or SKIPPED. A test that did not run gets no row (suite-level "not run" goes in summary.md).
  failure_message is cut to 300 characters and home-directory user names in paths are replaced.

Example
  tools/ci/results_to_csv.py --run-date 2026-10-27 --commit 0ed0fa12a0 --out docs/test-results/automated-test-results.csv \
      --junit android-native:livenessmanager:build/livenessmanager/test-results/testDebugUnitTest \
      --flutter flutter-face-liveness:app:/tmp/flutter-face_liveness.json
"""
import argparse, csv, glob, json, os, re, sys
import xml.etree.ElementTree as ET

COLUMNS = ["run_date", "commit", "suite", "module", "test_class", "test_name", "status", "duration_ms", "failure_message"]
USER_PATH = re.compile(r"(/Users/|/home/|C:\\Users\\)[^/\\\s]+")


def clean(msg):
    msg = " ".join((msg or "").split())
    msg = USER_PATH.sub(lambda m: m.group(1) + "<user>", msg)
    return msg[:300]


def junit_rows(suite, module, folder):
    files = sorted(glob.glob(os.path.join(folder, "*.xml")))
    if not files:
        sys.exit("no XML reports in " + folder)
    for f in files:
        root = ET.parse(f).getroot()
        cases = root.iter("testcase")
        for tc in cases:
            status, msg = "PASSED", ""
            for child in tc:
                if child.tag in ("failure", "error"):
                    status, msg = "FAILED", (child.get("message") or child.text or child.tag)
                elif child.tag == "skipped":
                    status, msg = "SKIPPED", (child.get("message") or "")
            yield [suite, module, tc.get("classname", ""), tc.get("name", ""), status,
                   int(round(float(tc.get("time") or 0) * 1000)), clean(msg)]


def flutter_rows(suite, module, path):
    suites, tests, errors = {}, {}, {}
    done = []
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            try:
                o = json.loads(line)
            except ValueError:
                continue
            t = o.get("type")
            if t == "suite":
                suites[o["suite"]["id"]] = o["suite"].get("path", "")
            elif t == "testStart":
                tests[o["test"]["id"]] = (o["test"], o.get("time", 0))
            elif t == "error":
                errors.setdefault(o["testID"], o.get("error", ""))
            elif t == "testDone":
                done.append(o)
    for o in done:
        if o.get("hidden"):
            continue
        test, start = tests[o["testID"]]
        if o.get("skipped"):
            status = "SKIPPED"
        elif o.get("result") == "success":
            status = "PASSED"
        else:
            status = "FAILED"
        cls = os.path.basename(suites.get(test.get("suiteID"), ""))
        yield [suite, module, cls, test.get("name", ""), status, max(0, o.get("time", 0) - start),
               clean(errors.get(o["testID"], "")) if status == "FAILED" else ""]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--run-date", required=True)
    ap.add_argument("--commit", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--junit", action="append", default=[])
    ap.add_argument("--flutter", action="append", default=[])
    a = ap.parse_args()
    rows = []
    for spec in a.junit:
        s, m, d = spec.split(":", 2)
        rows += list(junit_rows(s, m, d))
    for spec in a.flutter:
        s, m, p = spec.split(":", 2)
        rows += list(flutter_rows(s, m, p))
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(COLUMNS)
        for r in rows:
            w.writerow([a.run_date, a.commit] + r)
    totals = {}
    for r in rows:
        k = (r[0], r[4])
        totals[k] = totals.get(k, 0) + 1
    for (s, st), n in sorted(totals.items()):
        print(f"{s:28s} {st:8s} {n}")
    print("rows:", len(rows))


if __name__ == "__main__":
    main()
