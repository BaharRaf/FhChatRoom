from __future__ import annotations

import unittest

from recsys.evaluation import build_leave_one_out_targets
from recsys.evaluation import compare_metrics
from recsys.evaluation import evaluate_recommendations
from recsys.graphsage_prep import GraphSAGEConfig
from recsys.graphsage_prep import prepare_graphsage_training_data
from recsys.graphsage_train import GraphSAGETrainConfig
from recsys.graphsage_train import build_graphsage_firestore_payloads
from recsys.graphsage_train import train_graphsage_embeddings
from recsys.hin import build_hin
from recsys.models import DatasetConfig
from recsys.synthetic_data import generate_synthetic_dataset


class GraphSAGETrainTest(unittest.TestCase):
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
            seed=5,
        )
        train_dataset, held_out = build_leave_one_out_targets(dataset)
        hin = build_hin(train_dataset, max_topics=12)
        prep = prepare_graphsage_training_data(
            dataset=train_dataset,
            hin=hin,
            config=GraphSAGEConfig(seed=5),
        )
        self.dataset = train_dataset
        self.held_out = held_out
        self.hin = hin
        self.prep = prep

    def test_training_produces_embeddings_and_payloads(self) -> None:
        result = train_graphsage_embeddings(
            prep=self.prep,
            config=GraphSAGETrainConfig(epochs=25, learning_rate=0.05, seed=5),
        )
        self.assertTrue(result.embeddings)
        self.assertEqual(len(result.losses), 25)

        payloads = build_graphsage_firestore_payloads(self.dataset, self.hin, result, top_k=3)
        first_student_id = next(iter(self.dataset.students))
        self.assertIn(first_student_id, payloads)
        self.assertEqual(payloads[first_student_id]["recommendationSource"], "GRAPH_SAGE_LOCAL")

    def test_evaluation_returns_metrics(self) -> None:
        result = train_graphsage_embeddings(
            prep=self.prep,
            config=GraphSAGETrainConfig(epochs=20, learning_rate=0.05, seed=5),
        )
        ranked = {
            student_id: payload["recommendedRoomIds"]
            for student_id, payload in build_graphsage_firestore_payloads(self.dataset, self.hin, result, top_k=5).items()
        }
        metrics = evaluate_recommendations(self.dataset, ranked, self.held_out, k=5)
        self.assertGreaterEqual(metrics.precision_at_k, 0.0)
        self.assertGreaterEqual(metrics.recall_at_k, 0.0)
        self.assertGreaterEqual(metrics.ndcg_at_k, 0.0)
        self.assertGreaterEqual(metrics.hit_rate_at_k, 0.0)
        self.assertGreaterEqual(metrics.mean_reciprocal_rank, 0.0)

    def test_compare_metrics_returns_deltas(self) -> None:
        result = train_graphsage_embeddings(
            prep=self.prep,
            config=GraphSAGETrainConfig(epochs=10, learning_rate=0.05, seed=5),
        )
        ranked = {
            student_id: payload["recommendedRoomIds"]
            for student_id, payload in build_graphsage_firestore_payloads(self.dataset, self.hin, result, top_k=5).items()
        }
        metrics = evaluate_recommendations(self.dataset, ranked, self.held_out, k=5)
        deltas = compare_metrics(metrics, metrics)
        self.assertEqual(deltas["ndcgAtKDelta"], 0.0)
        self.assertEqual(deltas["mrrDelta"], 0.0)


if __name__ == "__main__":
    unittest.main()
