"""Unit tests for the evaluation metric math (eval/evaluate.py)."""
import math
import os
import sys
from typing import Optional

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "eval"))
from evaluate import Presentation, metrics_at, parse_label  # noqa: E402


def test_parse_label() -> None:
    assert parse_label("real_alice_1.mp4") == ("bonafide", "")
    assert parse_label("REAL_x.MOV") == ("bonafide", "")
    assert parse_label("attack_print_bob.mp4") == ("attack", "print")
    assert parse_label("attack_screen_ipad_2.mp4") == ("attack", "screen")
    assert parse_label("attack_x.mp4") == ("attack", "unknown")
    assert parse_label("clip.mp4") is None


def p(label: str, species: str, score: Optional[float]) -> Presentation:
    return Presentation("f", label, species, score=score)


def test_apcer_bpcer_definitions() -> None:
    vids = [p("bonafide", "", 0.9), p("bonafide", "", 0.4), p("bonafide", "", 0.8), p("bonafide", "", None),
            p("attack", "print", 0.6), p("attack", "print", 0.1),  # print: 1 of 2 accepted
            p("attack", "screen", 0.2), p("attack", "screen", 0.3),
            p("attack", "screen", 0.7), p("attack", "screen", 0.1),  # screen: 1 of 4 accepted
            p("attack", "replay", None)]  # failure to process -> excluded
    m = metrics_at(vids, 0.5)
    assert math.isclose(m.bpcer, 1 / 3)  # 0.4 rejected out of 3 scored bona fide
    assert m.per_species == {"print": 0.5, "screen": 0.25}
    assert m.apcer_max == 0.5  # worst species
    assert math.isclose(m.apcer_pooled, 2 / 6)


def test_threshold_is_inclusive_for_bonafide() -> None:
    m = metrics_at([p("bonafide", "", 0.5), p("attack", "print", 0.5)], 0.5)
    assert m.bpcer == 0 and m.apcer_max == 1  # score >= threshold => bona fide


def test_no_scored_presentations_gives_nan_not_crash() -> None:
    m = metrics_at([p("bonafide", "", None), p("attack", "print", None)], 0.5)
    assert math.isnan(m.bpcer) and math.isnan(m.apcer_max) and m.per_species == {}
