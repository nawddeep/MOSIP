"""
Unit & Integration Test Suite for Face Liveness & Verification Engines
"""
import os
import numpy as np
import pytest
from app.face_engine import FaceEngine
from app.liveness_engine import LivenessEngine
from app.database import UserDatabase

@pytest.fixture
def face_engine():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    yunet_path = os.path.join(base_dir, "models", "face_detection_yunet_2023mar.onnx")
    sface_path = os.path.join(base_dir, "models", "face_recognition_sface_2021dec.onnx")
    return FaceEngine(yunet_path, sface_path)

@pytest.fixture
def liveness_engine():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    model_path = os.path.join(base_dir, "models", "minifasnet_pad.onnx")
    return LivenessEngine(model_path)

@pytest.fixture
def user_database(tmp_path):
    db_file = tmp_path / "test_users_db.json"
    return UserDatabase(str(db_file))

def test_engine_initialization(face_engine, liveness_engine):
    assert face_engine is not None
    assert liveness_engine is not None

def test_database_crud(user_database):
    test_emb = np.random.randn(128).astype(np.float32)
    norm = np.linalg.norm(test_emb)
    test_emb = test_emb / norm if norm > 0 else test_emb
    
    # Save user
    success = user_database.save_user("test_001", "Jane Doe", "Resident", test_emb.tolist())
    assert success is True
    
    # Retrieve user
    users = user_database.get_all_users()
    assert "test_001" in users
    assert users["test_001"]["name"] == "Jane Doe"
    
    # Match user
    match = user_database.find_match(test_emb, threshold=0.363)
    assert match is not None
    assert match[0] == "test_001"
