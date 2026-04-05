from __future__ import annotations

import unittest

from recsys.graphsage_prep import GraphSAGEConfig
from recsys.graphsage_prep import prepare_graphsage_training_data
from recsys.hin import build_hin
from recsys.models import DatasetConfig
from recsys.synthetic_data import generate_synthetic_dataset


class GraphSAGEPrepTest(unittest.TestCase):
    def setUp(self) -> None:
        config = DatasetConfig(
            num_students=18,
            num_groups=7,
            num_topics=10,
            messages_per_day=15,
            num_days=4,
            min_groups_per_student=2,
            max_groups_per_student=3,
        )
        dataset = generate_synthetic_dataset(config=config, seed=12)
        hin = build_hin(dataset, max_topics=10)
        self.prep = prepare_graphsage_training_data(
            dataset=dataset,
            hin=hin,
            config=GraphSAGEConfig(seed=12, negative_samples_per_positive=2),
        )

    def test_feature_vectors_have_consistent_dimension(self) -> None:
        feature_dimension = len(self.prep.feature_names)
        self.assertGreater(feature_dimension, 0)
        self.assertTrue(self.prep.node_features)
        self.assertTrue(all(len(vector) == feature_dimension for vector in self.prep.node_features.values()))

    def test_pairs_and_splits_are_created(self) -> None:
        self.assertTrue(self.prep.positive_pairs)
        self.assertTrue(self.prep.negative_pairs)
        self.assertGreaterEqual(len(self.prep.warm_student_ids) + len(self.prep.cold_student_ids), 1)

    def test_summary_matches_prepared_data(self) -> None:
        summary = self.prep.summary()
        self.assertEqual(summary["featureDimension"], len(self.prep.feature_names))
        self.assertEqual(summary["numPositivePairs"], len(self.prep.positive_pairs))
        self.assertIn("MEMBER_OF", summary["relationCounts"])


if __name__ == "__main__":
    unittest.main()
