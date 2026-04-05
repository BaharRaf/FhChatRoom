from __future__ import annotations

from pathlib import Path
import unittest

from recsys.baselines import build_firestore_payloads
from recsys.firestore_json_adapter import dataset_from_firestore_json
from recsys.hin import build_hin


FIXTURE_DIR = Path(__file__).parent / "fixtures"


class FirestoreJsonAdapterTest(unittest.TestCase):
    def setUp(self) -> None:
        self.dataset = dataset_from_firestore_json(
            users_path=FIXTURE_DIR / "users.json",
            rooms_path=FIXTURE_DIR / "rooms.json",
            messages_path=FIXTURE_DIR / "messages.json",
        )
        self.hin = build_hin(self.dataset, max_topics=10)

    def test_adapter_loads_snapshot_counts(self) -> None:
        self.assertEqual(len(self.dataset.students), 3)
        self.assertEqual(len(self.dataset.groups), 3)
        self.assertEqual(len(self.dataset.messages), 3)

    def test_memberships_are_derived_from_rooms(self) -> None:
        anna = self.dataset.students["anna@stud.fh-campuswien.ac.at"]
        self.assertIn("room-algorithms", anna.joined_group_ids)

    def test_payload_matches_android_contract(self) -> None:
        payloads = build_firestore_payloads(self.dataset, self.hin, top_k=2)
        self.assertIn("anna@stud.fh-campuswien.ac.at", payloads)
        self.assertIn("recommendedRoomIds", payloads["anna@stud.fh-campuswien.ac.at"])
        self.assertEqual(payloads["anna@stud.fh-campuswien.ac.at"]["recommendationSource"], "CONTENT_BASED")


if __name__ == "__main__":
    unittest.main()
