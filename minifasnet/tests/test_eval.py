"""Unit tests for the evaluation metric math (eval/evaluate.py)."""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "eval"))
from evaluate import metrics_at, parse_label  # noqa: E402


def test_parse_label():
    assert parse_label("real_alice_1.mp4") == ("bonafide", None)
    assert parse_label("REAL_x.MOV") == ("bonafide", None)
    assert parse_label("attack_print_bob.mp4") == ("attack", "print")
    assert parse_label("attack_screen_ipad_2.mp4") == ("attack", "screen")
    assert parse_label("attack_x.mp4") == ("attack", "unknown")
    assert parse_label("clip.mp4") is None


def v(label, species, score):
    return dict(label=label, species=species, score=score)


def test_apcer_bpcer_definitions():
    vids = [v("bonafide", "", 0.9), v("bonafide", "", 0.4), v("bonafide", "", 0.8), v("bonafide", "", None),
            v("attack", "print", 0.6), v("attack", "print", 0.1),            # print: 1/2 accepted
            v("attack", "screen", 0.2), v("attack", "screen", 0.3), v("attack", "screen", 0.7), v("attack", "screen", 0.1),
            v("attack", "replay", None)]                                     # failure to process -> excluded
    m = metrics_at(vids, 0.5)
    assert math.isclose(m["bpcer"], 1 / 3)                 # 0.4 rejected out of 3 scored bona fide
    assert m["per_species"] == {"print": 0.5, "screen": 0.25}
    assert m["apcer_max"] == 0.5                           # worst species
    assert math.isclose(m["apcer_pooled"], 2 / 6)


def test_threshold_is_inclusive_for_bonafide():
    m = metrics_at([v("bonafide", "", 0.5), v("attack", "print", 0.5)], 0.5)
    assert m["bpcer"] == 0 and m["apcer_max"] == 1         # score >= threshold => bona fide
