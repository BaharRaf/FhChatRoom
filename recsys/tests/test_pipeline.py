from __future__ import annotations

import unittest

from recsys.baselines import build_firestore_payloads
from recsys.baselines import recommend_groups_for_student
from recsys.hin import build_hin
from recsys.models import DatasetConfig
from recsys.synthetic_data import generate_synthetic_dataset


class RecsysPipelineTest(unittest.TestCase):
    def setUp(self) -> None:
        config = DatasetConfig(
            num_students=20,
            num_groups=8,
            num_topics=10,
            messages_per_day=20,
            num_days=4,
            min_groups_per_student=2,
            max_groups_per_student=3,
        )
        self.dataset = generate_synthetic_dataset(config=config, seed=7)
        self.hin = build_hin(dataset=self.dataset, max_topics=config.num_topics)

    def test_synthetic_dataset_has_expected_size(self) -> None:
        self.assertEqual(len(self.dataset.students), 20)
        self.assertEqual(len(self.dataset.groups), 8)
        self.assertEqual(len(self.dataset.messages), 80)
        self.assertTrue(all(student.joined_group_ids for student in self.dataset.students.values()))

    def test_hin_contains_expected_node_and_edge_types(self) -> None:
        summary = self.hin.summary()
        self.assertIn("Student", summary["nodeCounts"])
        self.assertIn("Group", summary["nodeCounts"])
        self.assertIn("Message", summary["nodeCounts"])
        self.assertIn("Topic", summary["nodeCounts"])
        self.assertIn("MEMBER_OF", summary["edgeCounts"])
        self.assertIn("SENDS", summary["edgeCounts"])
        self.assertIn("POSTED_IN", summary["edgeCounts"])
        self.assertIn("CONTAINS", summary["edgeCounts"])
        self.assertIn("RELATED_TO", summary["edgeCounts"])

    def test_recommendations_exclude_joined_groups_and_match_app_contract(self) -> None:
        student_id, student = next(iter(self.dataset.students.items()))
        joined_groups = set(student.joined_group_ids)
        recommendations = recommend_groups_for_student(
            dataset=self.dataset,
            hin=self.hin,
            student_id=student_id,
            top_k=5,
        )

        self.assertTrue(recommendations)
        self.assertTrue(all(recommendation.group_id not in joined_groups for recommendation in recommendations))

        payloads = build_firestore_payloads(
            dataset=self.dataset,
            hin=self.hin,
            top_k=5,
        )
        self.assertIn(student_id, payloads)
        self.assertLessEqual(len(payloads[student_id]["recommendedRoomIds"]), 5)
        self.assertEqual(payloads[student_id]["recommendationSource"], "CONTENT_BASED")


if __name__ == "__main__":
    unittest.main()
