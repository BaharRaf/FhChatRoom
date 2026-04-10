from __future__ import annotations

import unittest

from recsys.evaluation import evaluate_recommendations
from recsys.evaluation import build_leave_one_out_targets
from recsys.graphsage_prep import GraphSAGEConfig
from recsys.graphsage_prep import prepare_graphsage_training_data
from recsys.hin import build_hin
from recsys.lightgcn_train import LightGCNConfig
from recsys.lightgcn_train import build_lightgcn_firestore_payloads
from recsys.lightgcn_train import train_lightgcn_embeddings
from recsys.models import DatasetConfig
from recsys.synthetic_data import generate_synthetic_dataset


class LightGCNTrainTest(unittest.TestCase):
    def setUp(self) -> None:
        dataset = generate_synthetic_dataset(
            config=DatasetConfig(
                num_students=24,
                num_groups=10,
                num_topics=12,
                messages_per_day=20,
                num_days=4,
                min_groups_per_student=2,
                max_groups_per_student=3,
            ),
            seed=11,
        )
        train_dataset, held_out = build_leave_one_out_targets(dataset)
        hin = build_hin(train_dataset, max_topics=12)
        prep = prepare_graphsage_training_data(
            dataset=train_dataset,
            hin=hin,
            config=GraphSAGEConfig(seed=11),
        )
        self.dataset = train_dataset
        self.held_out = held_out
        self.prep = prep

    def test_training_produces_embeddings_and_payloads(self) -> None:
        result = train_lightgcn_embeddings(
            prep=self.prep,
            config=LightGCNConfig(epochs=25, learning_rate=0.05, seed=11),
        )
        self.assertTrue(result.student_embeddings)
        self.assertTrue(result.group_embeddings)
        self.assertEqual(len(result.losses), 25)

        payloads = build_lightgcn_firestore_payloads(self.dataset, result, top_k=3)
        first_student_id = next(iter(self.dataset.students))
        self.assertIn(first_student_id, payloads)
        self.assertEqual(payloads[first_student_id]["recommendationSource"], "LIGHT_GCN_LOCAL")

    def test_evaluation_returns_metrics(self) -> None:
        result = train_lightgcn_embeddings(
            prep=self.prep,
            config=LightGCNConfig(epochs=20, learning_rate=0.05, seed=11),
        )
        ranked = {
            student_id: payload["recommendedRoomIds"]
            for student_id, payload in build_lightgcn_firestore_payloads(self.dataset, result, top_k=5).items()
        }
        metrics = evaluate_recommendations(self.dataset, ranked, self.held_out, k=5)
        self.assertGreaterEqual(metrics.precision_at_k, 0.0)
        self.assertGreaterEqual(metrics.recall_at_k, 0.0)
        self.assertGreaterEqual(metrics.ndcg_at_k, 0.0)
        self.assertGreaterEqual(metrics.hit_rate_at_k, 0.0)
        self.assertGreaterEqual(metrics.mean_reciprocal_rank, 0.0)


if __name__ == "__main__":
    unittest.main()
