import json
import os
import numpy as np
from typing import Dict, List, Optional

DB_FILE = os.path.join(os.path.dirname(__file__), "..", "data", "users_db.json")

class UserDatabase:
    def __init__(self, db_path: str = DB_FILE):
        self.db_path = db_path
        os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
        self.users: Dict[str, dict] = {}
        self.load()

    def load(self):
        if os.path.exists(self.db_path):
            try:
                with open(self.db_path, "r", encoding="utf-8") as f:
                    self.users = json.load(f)
            except Exception as e:
                print(f"[DB] Error loading DB: {e}")
                self.users = {}
        else:
            self.users = {}

    def save(self):
        try:
            with open(self.db_path, "w", encoding="utf-8") as f:
                json.dump(self.users, f, indent=2)
        except Exception as e:
            print(f"[DB] Error saving DB: {e}")

    def add_user(self, user_id: str, name: str, role: str, embedding: np.ndarray, thumbnail_b64: str = "") -> dict:
        user_data = {
            "user_id": user_id,
            "name": name,
            "role": role,
            "embedding": embedding.tolist() if isinstance(embedding, np.ndarray) else list(embedding),
            "thumbnail": thumbnail_b64,
            "created_at": str(np.datetime64("now"))
        }
        self.users[user_id] = user_data
        self.save()
        return user_data

    def get_user(self, user_id: str) -> Optional[dict]:
        return self.users.get(user_id)

    def list_users(self) -> List[dict]:
        return [
            {
                "user_id": u["user_id"],
                "name": u["name"],
                "role": u.get("role", "Resident"),
                "thumbnail": u.get("thumbnail", ""),
                "created_at": u.get("created_at", "")
            }
            for u in self.users.values()
        ]

    def delete_user(self, user_id: str) -> bool:
        if user_id in self.users:
            del self.users[user_id]
            self.save()
            return True
        return False
