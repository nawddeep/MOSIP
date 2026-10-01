"""Step 5 -- evaluate MiniFASNetPAD on your own labelled recordings (ISO/IEC 30107-3 style metrics).

Input folder, file naming (case-insensitive):
    real_<anything>.<ext>                     bona fide presentation
    attack_<species>_<anything>.<ext>         attack; species = print | screen | replay | <any word>
    attack_<anything>.<ext>                   attack with unknown species ("unknown")
  ext: mp4 mov avi mkv webm (video) or jpg jpeg png bmp (single image)

Face box source (the module itself never detects faces):
    --box-source haar      DEV ONLY (OpenCV Haar cascade, largest face per frame). Not the production detector.
    --box-source csv --box-csv boxes.csv
                           boxes produced by YOUR detector (e.g. MediaPipe), columns: file,frame,x,y,w,h
                           ('file' = file name, 'frame' = 0-based frame index). Only listed frames are scored.

Outputs (in --out): frames.csv, videos.csv, sweep.csv, summary.md

Metric definitions used (see README "Evaluation" for the full statement):
  * One presentation = one file. Its score = aggregate (--aggregate, default mean) of its frame real_scores.
  * Decision: bona fide if score >= threshold, else attack.
  * APCER(species) = fraction of that species' attack presentations classified as bona fide.
  * APCER (reported)  = MAX over species (worst case), per ISO/IEC 30107-3. Pooled APCER also shown for info.
  * BPCER = fraction of bona fide presentations classified as attack.
  * Files with zero scorable frames (no face / error) are "failures to process": excluded from APCER/BPCER and
    reported separately (FTP counts).
"""
import argparse
import csv
import os
import re
import sys
from collections import defaultdict

import cv2
import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "python"))
from minifasnet_pad import MiniFASNetPAD, PADError  # noqa: E402

VIDEO_EXT = {".mp4", ".mov", ".avi", ".mkv", ".webm"}
IMAGE_EXT = {".jpg", ".jpeg", ".png", ".bmp"}


def parse_label(fname):
    """-> ('bonafide', None) | ('attack', species) | None if the name does not follow the convention."""
    stem = os.path.splitext(fname)[0].lower()
    if stem.startswith("real_") or stem == "real":
        return "bonafide", None
    m = re.match(r"^attack_([a-z0-9]+)_.+$", stem)
    if m:
        return "attack", m.group(1)
    if stem.startswith("attack"):
        return "attack", "unknown"
    return None


def iter_frames(path, sample_fps, max_frames, wanted=None):
    """Yield (frame_index, bgr). wanted=set of indices -> only those (csv box mode)."""
    if os.path.splitext(path)[1].lower() in IMAGE_EXT:
        img = cv2.imread(path)
        if img is not None and (wanted is None or 0 in wanted):
            yield 0, img
        return
    cap = cv2.VideoCapture(path)  # OpenCV >=4.5 applies the rotation metadata of phone videos automatically
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    step = max(1, int(round(fps / sample_fps)))
    idx, n = 0, 0
    while n < max_frames:
        ok, frame = cap.read()
        if not ok:
            break
        if (wanted is not None and idx in wanted) or (wanted is None and idx % step == 0):
            yield idx, frame
            n += 1
        idx += 1
    cap.release()


class HaarBoxes:
    """DEV ONLY -- not for production. Largest Haar face per frame."""

    def __init__(self):
        self.c = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")

    def get(self, fname, idx, frame):
        f = self.c.detectMultiScale(cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY), 1.1, 5, minSize=(40, 40))
        return None if len(f) == 0 else tuple(float(v) for v in max(f, key=lambda b: b[2] * b[3]))


class CsvBoxes:
    def __init__(self, path):
        self.b = defaultdict(dict)
        with open(path, newline="") as fh:
            for r in csv.DictReader(fh):
                self.b[r["file"]][int(r["frame"])] = tuple(float(r[k]) for k in ("x", "y", "w", "h"))

    def frames_for(self, fname):
        return set(self.b.get(fname, {}).keys())

    def get(self, fname, idx, frame):
        return self.b.get(fname, {}).get(idx)


def metrics_at(videos, t):
    """videos: list of dicts with label, species, score (None = failure to process)."""
    bf = [v for v in videos if v["label"] == "bonafide" and v["score"] is not None]
    at = [v for v in videos if v["label"] == "attack" and v["score"] is not None]
    bpcer = (sum(v["score"] < t for v in bf) / len(bf)) if bf else float("nan")
    per = {}
    for s in sorted({v["species"] for v in at}):
        xs = [v for v in at if v["species"] == s]
        per[s] = sum(v["score"] >= t for v in xs) / len(xs)
    apcer_max = max(per.values()) if per else float("nan")
    apcer_pooled = (sum(v["score"] >= t for v in at) / len(at)) if at else float("nan")
    return dict(threshold=t, bpcer=bpcer, apcer_max=apcer_max, apcer_pooled=apcer_pooled, per_species=per)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data", required=True, help="folder of real_*/attack_* files")
    ap.add_argument("--out", required=True)
    ap.add_argument("--box-source", choices=["haar", "csv"], default="haar")
    ap.add_argument("--box-csv")
    ap.add_argument("--model-dir", default=None)
    ap.add_argument("--sample-fps", type=float, default=5.0, help="frames sampled per second of video")
    ap.add_argument("--max-frames", type=int, default=50, help="max scored frames per file")
    ap.add_argument("--aggregate", choices=["mean", "median", "min"], default="mean")
    ap.add_argument("--threshold", type=float, default=0.5, help="bona fide if score >= threshold")
    ap.add_argument("--target-bpcer", type=float, default=0.05)
    ap.add_argument("--min-face-size", type=int, default=32)
    a = ap.parse_args(argv)
    if a.box_source == "csv" and not a.box_csv:
        ap.error("--box-source csv needs --box-csv")
    os.makedirs(a.out, exist_ok=True)

    pad = MiniFASNetPAD(a.model_dir, min_face_size=a.min_face_size) if a.model_dir else \
        MiniFASNetPAD(min_face_size=a.min_face_size)
    boxes = HaarBoxes() if a.box_source == "haar" else CsvBoxes(a.box_csv)
    agg = {"mean": np.mean, "median": np.median, "min": np.min}[a.aggregate]

    files, skipped = [], []
    for f in sorted(os.listdir(a.data)):
        ext = os.path.splitext(f)[1].lower()
        if ext not in VIDEO_EXT | IMAGE_EXT:
            continue
        lab = parse_label(f)
        (files if lab else skipped).append((f, lab))
    if skipped:
        print(f"WARNING: {len(skipped)} file(s) ignored (name must start with real_ or attack_): "
              + ", ".join(s for s, _ in skipped[:5]))
    if not files:
        sys.exit("no labelled files found")

    frame_rows, videos = [], []
    for f, (label, species) in files:
        wanted = boxes.frames_for(f) if isinstance(boxes, CsvBoxes) else None
        scores, n_seen = [], 0
        for idx, frame in iter_frames(os.path.join(a.data, f), a.sample_fps, a.max_frames, wanted):
            n_seen += 1
            box = boxes.get(f, idx, frame)
            if box is None:
                frame_rows.append([f, label, species or "", idx, "NO_FACE", "", ""])
                continue
            try:
                r = pad.predict(frame, box)
                scores.append(r.real_score)
                frame_rows.append([f, label, species or "", idx, "OK", f"{r.real_score:.6f}", r.label])
            except PADError as e:
                frame_rows.append([f, label, species or "", idx, type(e).__name__, "", ""])
        score = float(agg(scores)) if scores else None
        videos.append(dict(file=f, label=label, species=species or "", frames_seen=n_seen, frames_ok=len(scores),
                           score=score))
        print(f"{f}: {len(scores)}/{n_seen} frames scored, {a.aggregate} real_score="
              + (f"{score:.4f}" if score is not None else "n/a (failure to process)"))

    with open(os.path.join(a.out, "frames.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["file", "label", "species", "frame", "status", "real_score", "frame_decision"])
        w.writerows(frame_rows)
    with open(os.path.join(a.out, "videos.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["file", "label", "species", "frames_seen", "frames_ok", f"{a.aggregate}_real_score",
                    f"decision@{a.threshold}"])
        for v in videos:
            dec = "" if v["score"] is None else ("bonafide" if v["score"] >= a.threshold else "attack")
            w.writerow([v["file"], v["label"], v["species"], v["frames_seen"], v["frames_ok"],
                        "" if v["score"] is None else f"{v['score']:.6f}", dec])

    # Threshold sweep: a fixed grid plus every observed score (exact operating points).
    obs = [v["score"] for v in videos if v["score"] is not None]
    grid = sorted(set(np.round(np.linspace(0, 1, 101), 4).tolist()) | set(obs) | {a.threshold})
    species = sorted({v["species"] for v in videos if v["label"] == "attack" and v["score"] is not None})
    sweep = [metrics_at(videos, t) for t in grid]
    with open(os.path.join(a.out, "sweep.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["threshold", "BPCER", "APCER_max", "APCER_pooled"] + [f"APCER_{s}" for s in species])
        for m in sweep:
            w.writerow([f"{m['threshold']:.6f}", f"{m['bpcer']:.4f}", f"{m['apcer_max']:.4f}",
                        f"{m['apcer_pooled']:.4f}"] + [f"{m['per_species'].get(s, float('nan')):.4f}" for s in species])

    # Highest threshold with BPCER <= target (BPCER only grows with the threshold; highest => lowest APCER).
    ok = [m for m in sweep if m["bpcer"] <= a.target_bpcer]
    best = max(ok, key=lambda m: m["threshold"]) if ok else None
    at_t = metrics_at(videos, a.threshold)
    n = lambda lab: sum(v["label"] == lab and v["score"] is not None for v in videos)
    ftp = lambda lab: sum(v["label"] == lab and v["score"] is None for v in videos)

    fmt = lambda m: (f"BPCER={m['bpcer']:.4f}  APCER(max over species)={m['apcer_max']:.4f}  "
                     f"APCER(pooled)={m['apcer_pooled']:.4f}  per species: "
                     + ", ".join(f"{s}={x:.4f}" for s, x in m["per_species"].items()))
    lines = ["# MiniFASNet evaluation summary", "",
             f"- data: `{a.data}`  box source: **{a.box_source}**" + (" (DEV ONLY)" if a.box_source == "haar" else ""),
             f"- presentations scored: bona fide {n('bonafide')}, attack {n('attack')} "
             f"(species: {', '.join(species) or '-'})",
             f"- failures to process (no scorable frame, excluded): bona fide {ftp('bonafide')}, attack {ftp('attack')}",
             f"- per-presentation score = {a.aggregate} of frame real_scores; sample_fps={a.sample_fps}, "
             f"max_frames={a.max_frames}", "",
             f"## At threshold {a.threshold}", "", fmt(at_t), "",
             f"## Threshold for BPCER <= {a.target_bpcer}", ""]
    lines.append((f"threshold = **{best['threshold']:.6f}** -> " + fmt(best)) if best else
                 "no threshold reaches the target BPCER")
    lines += ["", "## Sweep (excerpt; full table in sweep.csv)", "",
              "| threshold | BPCER | APCER_max | " + " | ".join(f"APCER_{s}" for s in species) + " |",
              "|---|---|---|" + "---|" * len(species)]
    for m in sweep:
        if abs(m["threshold"] * 10 - round(m["threshold"] * 10)) < 1e-9:  # every 0.1
            lines.append(f"| {m['threshold']:.1f} | {m['bpcer']:.4f} | {m['apcer_max']:.4f} | "
                         + " | ".join(f"{m['per_species'].get(s, float('nan')):.4f}" for s in species) + " |")
    if n("bonafide") + n("attack") < 50:
        lines += ["", f"NOTE: only {n('bonafide') + n('attack')} presentations -- far too few for meaningful error "
                      "rates. Collect many subjects, sessions, devices and attack instruments."]
    open(os.path.join(a.out, "summary.md"), "w").write("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
