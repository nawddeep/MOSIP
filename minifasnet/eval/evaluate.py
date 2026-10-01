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
  * APCER (reported)  = MAX over species (worst case). Pooled APCER also shown for info.
  * BPCER = fraction of bona fide presentations classified as attack.
  * Files with zero scorable frames (no face / error) are "failures to process": excluded from APCER/BPCER and
    reported separately (FTP counts).
"""
import argparse
import csv
import math
import os
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from typing import Dict, Iterator, List, Optional, Set, Tuple

import cv2
import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "python"))
from dev_box import dev_get_box  # noqa: E402
from minifasnet_pad import MiniFASNetPAD, PADError  # noqa: E402

VIDEO_EXT = {".mp4", ".mov", ".avi", ".mkv", ".webm"}
IMAGE_EXT = {".jpg", ".jpeg", ".png", ".bmp"}
Box = Tuple[float, float, float, float]


@dataclass
class Presentation:
    file: str
    label: str  # "bonafide" | "attack"
    species: str  # attack species ("" for bona fide)
    frames_seen: int = 0
    frames_ok: int = 0
    score: Optional[float] = None  # None = failure to process


@dataclass
class Metrics:
    threshold: float
    bpcer: float
    apcer_max: float
    apcer_pooled: float
    per_species: Dict[str, float] = field(default_factory=dict)


def parse_label(fname: str) -> Optional[Tuple[str, str]]:
    """-> ('bonafide', '') | ('attack', species) | None if the name does not follow the convention."""
    stem = os.path.splitext(fname)[0].lower()
    if stem.startswith("real_") or stem == "real":
        return "bonafide", ""
    m = re.match(r"^attack_([a-z0-9]+)_.+$", stem)
    if m:
        return "attack", m.group(1)
    if stem.startswith("attack"):
        return "attack", "unknown"
    return None


def iter_frames(path: str, sample_fps: float, max_frames: int,
                wanted: Optional[Set[int]] = None) -> Iterator[Tuple[int, np.ndarray]]:
    """Yield (frame_index, bgr). wanted = set of indices -> only those (csv box mode)."""
    if os.path.splitext(path)[1].lower() in IMAGE_EXT:
        img = cv2.imread(path)
        if img is None:
            print(f"WARNING: cannot read image {path}", file=sys.stderr)
        elif wanted is None or 0 in wanted:
            yield 0, img
        return
    cap = cv2.VideoCapture(path)  # OpenCV >= 4.5 applies the rotation metadata of phone videos automatically
    if not cap.isOpened():
        print(f"WARNING: cannot open video {path}", file=sys.stderr)
        return
    fps = cap.get(cv2.CAP_PROP_FPS)
    if not math.isfinite(fps) or fps <= 0:
        fps = 30.0  # unknown frame rate -> assume 30
    step = max(1, int(round(fps / sample_fps)))
    idx, n = 0, 0
    try:
        while n < max_frames:
            ok, frame = cap.read()
            if not ok or frame is None:
                break
            if (wanted is not None and idx in wanted) or (wanted is None and idx % step == 0):
                yield idx, frame
                n += 1
            idx += 1
    finally:
        cap.release()


class HaarBoxes:
    """DEV ONLY -- not for production. Largest Haar face per frame (python/dev_box.py)."""

    def frames_for(self, fname: str) -> Optional[Set[int]]:
        return None  # sample frames by rate

    def get(self, fname: str, idx: int, frame: np.ndarray) -> Optional[Box]:
        return dev_get_box(frame)


class CsvBoxes:
    """Boxes from your own detector: CSV columns file,frame,x,y,w,h."""

    def __init__(self, path: str):
        self.b: Dict[str, Dict[int, Box]] = defaultdict(dict)
        with open(path, newline="") as fh:
            reader = csv.DictReader(fh)
            missing = {"file", "frame", "x", "y", "w", "h"} - set(reader.fieldnames or [])
            if missing:
                raise ValueError(f"{path}: missing columns {sorted(missing)}")
            for line_no, r in enumerate(reader, start=2):
                try:
                    box = (float(r["x"]), float(r["y"]), float(r["w"]), float(r["h"]))
                    self.b[r["file"]][int(r["frame"])] = box
                except (TypeError, ValueError):
                    raise ValueError(f"{path}:{line_no}: bad row {r}") from None

    def frames_for(self, fname: str) -> Optional[Set[int]]:
        return set(self.b.get(fname, {}).keys())

    def get(self, fname: str, idx: int, frame: np.ndarray) -> Optional[Box]:
        return self.b.get(fname, {}).get(idx)


def metrics_at(presentations: List[Presentation], t: float) -> Metrics:
    """APCER/BPCER at threshold t over presentations with a score (failures to process are excluded)."""
    bona = [p.score for p in presentations if p.label == "bonafide" and p.score is not None]
    attacks = [(p.species, p.score) for p in presentations if p.label == "attack" and p.score is not None]
    bpcer = sum(s < t for s in bona) / len(bona) if bona else float("nan")
    per: Dict[str, float] = {}
    for sp in sorted({sp for sp, _ in attacks}):
        xs = [s for k, s in attacks if k == sp]
        per[sp] = sum(s >= t for s in xs) / len(xs)
    apcer_max = max(per.values()) if per else float("nan")
    apcer_pooled = sum(s >= t for _, s in attacks) / len(attacks) if attacks else float("nan")
    return Metrics(t, bpcer, apcer_max, apcer_pooled, per)


def fmt_metrics(m: Metrics) -> str:
    return (f"BPCER={m.bpcer:.4f}  APCER(max over species)={m.apcer_max:.4f}  APCER(pooled)={m.apcer_pooled:.4f}  "
            "per species: " + ", ".join(f"{s}={x:.4f}" for s, x in m.per_species.items()))


def parse_args(argv: Optional[List[str]]) -> argparse.Namespace:
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
    if not os.path.isdir(a.data):
        ap.error(f"--data is not a folder: {a.data}")
    if a.sample_fps <= 0:
        ap.error("--sample-fps must be > 0")
    if a.max_frames < 1:
        ap.error("--max-frames must be >= 1")
    if not 0 <= a.threshold <= 1 or not 0 <= a.target_bpcer <= 1:
        ap.error("--threshold and --target-bpcer must be within [0, 1]")
    if a.min_face_size < 0:
        ap.error("--min-face-size must be >= 0")
    return a


def main(argv: Optional[List[str]] = None) -> int:
    a = parse_args(argv)
    os.makedirs(a.out, exist_ok=True)
    pad = MiniFASNetPAD(a.model_dir, min_face_size=a.min_face_size) if a.model_dir else \
        MiniFASNetPAD(min_face_size=a.min_face_size)
    boxes = HaarBoxes() if a.box_source == "haar" else CsvBoxes(a.box_csv)
    agg = {"mean": np.mean, "median": np.median, "min": np.min}[a.aggregate]

    files: List[Tuple[str, str, str]] = []
    skipped: List[str] = []
    for f in sorted(os.listdir(a.data)):
        if os.path.splitext(f)[1].lower() not in VIDEO_EXT | IMAGE_EXT:
            continue
        lab = parse_label(f)
        if lab is None:
            skipped.append(f)
        else:
            files.append((f, lab[0], lab[1]))
    if skipped:
        print(f"WARNING: {len(skipped)} file(s) ignored (name must start with real_ or attack_): "
              + ", ".join(skipped[:5]), file=sys.stderr)
    if not files:
        print("no labelled files found", file=sys.stderr)
        return 1

    frame_rows: List[list] = []
    presentations: List[Presentation] = []
    for f, label, species in files:
        p = Presentation(f, label, species)
        scores: List[float] = []
        for idx, frame in iter_frames(os.path.join(a.data, f), a.sample_fps, a.max_frames, boxes.frames_for(f)):
            p.frames_seen += 1
            box = boxes.get(f, idx, frame)
            if box is None:
                frame_rows.append([f, label, species, idx, "NO_FACE", "", ""])
                continue
            try:
                r = pad.predict(frame, box)
            except PADError as e:
                frame_rows.append([f, label, species, idx, type(e).__name__, "", ""])
                continue
            scores.append(r.real_score)
            frame_rows.append([f, label, species, idx, "OK", f"{r.real_score:.6f}", r.label])
        p.frames_ok = len(scores)
        p.score = float(agg(scores)) if scores else None
        presentations.append(p)
        print(f"{f}: {p.frames_ok}/{p.frames_seen} frames scored, {a.aggregate} real_score="
              + (f"{p.score:.4f}" if p.score is not None else "n/a (failure to process)"))

    with open(os.path.join(a.out, "frames.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["file", "label", "species", "frame", "status", "real_score", "frame_decision"])
        w.writerows(frame_rows)
    with open(os.path.join(a.out, "videos.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["file", "label", "species", "frames_seen", "frames_ok", f"{a.aggregate}_real_score",
                    f"decision@{a.threshold}"])
        for p in presentations:
            if p.score is None:
                w.writerow([p.file, p.label, p.species, p.frames_seen, p.frames_ok, "", ""])
            else:
                dec = "bonafide" if p.score >= a.threshold else "attack"
                w.writerow([p.file, p.label, p.species, p.frames_seen, p.frames_ok, f"{p.score:.6f}", dec])

    # Threshold sweep: a fixed grid plus every observed score (exact operating points).
    observed = [p.score for p in presentations if p.score is not None]
    grid = sorted(set(np.round(np.linspace(0, 1, 101), 4).tolist()) | set(observed) | {a.threshold})
    species = sorted({p.species for p in presentations if p.label == "attack" and p.score is not None})
    sweep = [metrics_at(presentations, t) for t in grid]
    with open(os.path.join(a.out, "sweep.csv"), "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["threshold", "BPCER", "APCER_max", "APCER_pooled"] + [f"APCER_{s}" for s in species])
        for m in sweep:
            w.writerow([f"{m.threshold:.6f}", f"{m.bpcer:.4f}", f"{m.apcer_max:.4f}", f"{m.apcer_pooled:.4f}"]
                       + [f"{m.per_species.get(s, float('nan')):.4f}" for s in species])

    # Highest threshold with BPCER <= target (BPCER only grows with the threshold; highest => lowest APCER).
    ok = [m for m in sweep if m.bpcer <= a.target_bpcer]
    best = max(ok, key=lambda m: m.threshold) if ok else None
    at_t = metrics_at(presentations, a.threshold)
    n_bf = sum(p.label == "bonafide" and p.score is not None for p in presentations)
    n_at = sum(p.label == "attack" and p.score is not None for p in presentations)
    ftp_bf = sum(p.label == "bonafide" and p.score is None for p in presentations)
    ftp_at = sum(p.label == "attack" and p.score is None for p in presentations)

    lines = ["# MiniFASNet evaluation summary", "",
             f"- data: `{a.data}`  box source: **{a.box_source}**" + (" (DEV ONLY)" if a.box_source == "haar" else ""),
             f"- presentations scored: bona fide {n_bf}, attack {n_at} (species: {', '.join(species) or '-'})",
             f"- failures to process (no scorable frame, excluded): bona fide {ftp_bf}, attack {ftp_at}",
             f"- per-presentation score = {a.aggregate} of frame real_scores; sample_fps={a.sample_fps}, "
             f"max_frames={a.max_frames}", "",
             f"## At threshold {a.threshold}", "", fmt_metrics(at_t), "",
             f"## Threshold for BPCER <= {a.target_bpcer}", "",
             (f"threshold = **{best.threshold:.6f}** -> " + fmt_metrics(best)) if best else
             "no threshold reaches the target BPCER",
             "", "## Sweep (excerpt; full table in sweep.csv)", "",
             "| threshold | BPCER | APCER_max | " + " | ".join(f"APCER_{s}" for s in species) + " |",
             "|---|---|---|" + "---|" * len(species)]
    for m in sweep:
        if abs(m.threshold * 10 - round(m.threshold * 10)) < 1e-9:  # every 0.1
            lines.append(f"| {m.threshold:.1f} | {m.bpcer:.4f} | {m.apcer_max:.4f} | "
                         + " | ".join(f"{m.per_species.get(s, float('nan')):.4f}" for s in species) + " |")
    if n_bf + n_at < 50:
        lines += ["", f"NOTE: only {n_bf + n_at} presentations -- far too few for meaningful error rates. "
                      "Collect many subjects, sessions, devices and attack instruments."]
    with open(os.path.join(a.out, "summary.md"), "w") as fh:
        fh.write("\n".join(lines) + "\n")
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    sys.exit(main())
