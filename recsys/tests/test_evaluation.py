from __future__ import annotations

import unittest

from recsys.evaluation import RankingMetrics
from recsys.evaluation import build_leave_one_out_targets
from recsys.evaluation import robustness_ratio
from recsys.models import DatasetConfig
from recsys.models import Message
from recsys.synthetic_data import generate_synthetic_dataset


class EvaluationHelpersTest(unittest.TestCase):
    def test_leave_one_out_removes_held_out_membership_messages(self) -> None:
        dataset = generate_synthetic_dataset(
            config=DatasetConfig(
                num_students=10,
                num_groups=5,
                num_topics=8,
                messages_per_day=10,
                num_days=2,
                min_groups_per_student=2,
                max_groups_per_student=3,
            ),
            seed=13,
        )
        student = next(iter(dataset.students.values()))
        held_out_group_id = student.joined_group_ids[-1]
        extra_message = Message(
            id="message-held-out-test",
            sender_id=student.id,
            group_id=held_out_group_id,
            text="held out interaction",
            day=1,
            reaction_count=0,
        )
        dataset.messages.append(extra_message)

        train_dataset, held_out = build_leave_one_out_targets(dataset)

        self.assertEqual(held_out[student.id], [held_out_group_id])
        self.assertNotIn(held_out_group_id, train_dataset.students[student.id].joined_group_ids)
        self.assertFalse(
            any(message.id == extra_message.id for message in train_dataset.messages)
        )

    def test_robustness_ratio_uses_cold_over_warm_ndcg(self) -> None:
        warm = RankingMetrics(
            precision_at_k=0.0,
            recall_at_k=0.0,
            ndcg_at_k=0.8,
            hit_rate_at_k=0.0,
            mean_reciprocal_rank=0.0,
            evaluated_users=1,
        )
        cold = RankingMetrics(
            precision_at_k=0.0,
            recall_at_k=0.0,
            ndcg_at_k=0.4,
            hit_rate_at_k=0.0,
            mean_reciprocal_rank=0.0,
            evaluated_users=1,
        )

        self.assertEqual(robustness_ratio(cold, warm), 0.5)


if __name__ == "__main__":
    unittest.main()
