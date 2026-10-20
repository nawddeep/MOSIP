"""
Unit & Integration Test Suite for Face Liveness & Verification Engines
"""
import os
import numpy as np
import pytest
from app.face_engine import FaceEngine
from app.liveness_engine import LivenessEngine
from app.database import UserDatabase

MODELS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "models")


def unit_vector(seed: int) -> np.ndarray:
    v = np.random.default_rng(seed).standard_normal(128).astype(np.float32)
    return v / np.linalg.norm(v)


@pytest.fixture
def face_engine():
    return FaceEngine(MODELS_DIR)


@pytest.fixture
def liveness_engine():
    return LivenessEngine(MODELS_DIR)


@pytest.fixture
def user_database(tmp_path):
    db_file = tmp_path / "test_users_db.json"
    return UserDatabase(str(db_file))


def test_engine_initialization(face_engine, liveness_engine):
    assert face_engine.detector is not None
    assert face_engine.recognizer is not None
    assert liveness_engine.has_onnx_model is True
    assert liveness_engine.pad_session is not None


def test_missing_models_fail_loudly(tmp_path):
    with pytest.raises(FileNotFoundError):
        FaceEngine(str(tmp_path))


def test_database_crud(user_database):
    emb = unit_vector(1)

    # Save user
    saved = user_database.add_user("test_001", "Jane Doe", "Resident", emb)
    assert saved["user_id"] == "test_001"

    # Retrieve user
    assert user_database.get_user("test_001")["name"] == "Jane Doe"
    assert [u["user_id"] for u in user_database.list_users()] == ["test_001"]

    # Persisted to disk: a fresh instance on the same file sees the user
    reloaded = UserDatabase(user_database.db_path)
    assert np.allclose(reloaded.get_user("test_001")["embedding"], emb)

    # Delete user
    assert user_database.delete_user("test_001") is True
    assert user_database.get_user("test_001") is None
    assert user_database.delete_user("test_001") is False


def test_face_matching(face_engine):
    emb = unit_vector(2)
    same_sim, same_pct, same_match = face_engine.compare_faces(emb, emb)
    assert same_match is True
    assert same_sim == pytest.approx(1.0, abs=1e-5)
    assert same_pct == 100.0

    diff_sim, _, diff_match = face_engine.compare_faces(emb, -emb)
    assert diff_match is False
    assert diff_sim < 0.363


def test_no_face_in_blank_image(face_engine):
    blank = np.zeros((480, 640, 3), dtype=np.uint8)
    assert face_engine.detect_faces(blank) == []


def test_random_challenge_is_supported(liveness_engine):
    for _ in range(20):
        challenge = liveness_engine.initiate_random_challenge()
        assert challenge in liveness_engine.supported_challenges
    liveness_engine.reset_challenge()
    assert liveness_engine.challenge_completed is False
