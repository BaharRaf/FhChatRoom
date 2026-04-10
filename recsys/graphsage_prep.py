from __future__ import annotations

import importlib.util
import random
from collections import Counter
from collections import defaultdict
from dataclasses import asdict
from dataclasses import dataclass
from statistics import median
from typing import Any

from recsys.models import HINGraph
from recsys.models import SyntheticDataset


def torch_available() -> bool:
    return importlib.util.find_spec("torch") is not None


@dataclass(frozen=True)
class GraphSAGEConfig:
    hidden_dim: int = 64
    num_layers: int = 2
    max_neighbors: int = 15
    batch_size: int = 128
    negative_samples_per_positive: int = 3
    hard_negative_ratio: float = 0.67
    hard_negative_pool_factor: int = 4
    cold_start_interaction_threshold: int = 5
    seed: int = 42


@dataclass
class GraphSAGEPreparedData:
    config: GraphSAGEConfig
    feature_names: list[str]
    node_features: dict[str, list[float]]
    node_types: dict[str, str]
    adjacency: dict[str, list[str]]
    relation_adjacency: dict[str, dict[str, list[str]]]
    positive_pairs: list[tuple[str, str]]
    negative_pairs: list[tuple[str, str]]
    training_triplets: list[tuple[str, str, str]]
    warm_student_ids: list[str]
    cold_student_ids: list[str]
    relation_counts: dict[str, int]
    negative_pair_sources: dict[str, int]
    selected_topics: list[str]
    torch_available: bool

    def summary(self) -> dict[str, Any]:
        return {
            "config": asdict(self.config),
            "numNodes": len(self.node_features),
            "featureDimension": len(self.feature_names),
            "numPositivePairs": len(self.positive_pairs),
            "numNegativePairs": len(self.negative_pairs),
            "numTrainingTriplets": len(self.training_triplets),
            "warmStudents": len(self.warm_student_ids),
            "coldStudents": len(self.cold_student_ids),
            "relationCounts": self.relation_counts,
            "negativePairSources": self.negative_pair_sources,
            "selectedTopics": self.selected_topics,
            "torchAvailable": self.torch_available,
        }


def _build_feature_catalog(dataset: SyntheticDataset, hin: HINGraph) -> list[str]:
    study_paths = sorted({student.study_path for student in dataset.students.values() if student.study_path})
    semester_buckets = sorted({student.semester_bucket for student in dataset.students.values() if student.semester_bucket})
    categories = sorted({group.category for group in dataset.groups.values() if group.category})
    primary_study_paths = sorted({group.primary_study_path for group in dataset.groups.values() if group.primary_study_path})

    feature_names = [
        "numeric:semester_norm",
        "numeric:member_count_norm",
        "numeric:message_length_norm",
        "numeric:reaction_count_norm",
        "numeric:day_norm",
        "numeric:is_student",
        "numeric:is_group",
        "numeric:is_message",
        "numeric:is_topic",
    ]

    feature_names.extend(f"study_path:{study_path}" for study_path in study_paths)
    feature_names.extend(f"semester_bucket:{bucket}" for bucket in semester_buckets)
    feature_names.extend(f"category:{category}" for category in categories)
    feature_names.extend(f"primary_study_path:{study_path}" for study_path in primary_study_paths)
    feature_names.extend(f"topic:{topic}" for topic in hin.selected_topics)

    return feature_names


def _one_hot_updates(feature_names: list[str], values: list[str]) -> dict[str, float]:
    available = set(feature_names)
    return {value: 1.0 for value in values if value in available}


def _vectorize(feature_names: list[str], updates: dict[str, float]) -> list[float]:
    return [updates.get(name, 0.0) for name in feature_names]


def _message_stats(dataset: SyntheticDataset) -> tuple[int, int, int]:
    max_length = max((len(message.text.split()) for message in dataset.messages), default=1)
    max_reaction_count = max((message.reaction_count for message in dataset.messages), default=1)
    max_day = max((message.day for message in dataset.messages), default=1)
    return max_length, max_reaction_count, max_day


def _message_counts_by_sender(dataset: SyntheticDataset) -> dict[str, int]:
    counts: dict[str, int] = Counter()
    for message in dataset.messages:
        counts[message.sender_id] += 1
    return counts


def _student_vectors(
    dataset: SyntheticDataset,
    hin: HINGraph,
    feature_names: list[str],
) -> dict[str, list[float]]:
    vectors: dict[str, list[float]] = {}
    max_semester = max((student.semester for student in dataset.students.values()), default=1)

    for student in dataset.students.values():
        updates: dict[str, float] = {
            "numeric:semester_norm": student.semester / max(max_semester, 1),
            "numeric:is_student": 1.0,
        }
        updates.update(
            _one_hot_updates(
                feature_names,
                [
                    f"study_path:{student.study_path}",
                    f"semester_bucket:{student.semester_bucket}",
                ],
            )
        )
        for topic, weight in hin.student_topic_weights.get(student.id, {}).items():
            updates[f"topic:{topic}"] = weight
        vectors[student.id] = _vectorize(feature_names, updates)

    return vectors


def _group_vectors(
    dataset: SyntheticDataset,
    hin: HINGraph,
    feature_names: list[str],
) -> dict[str, list[float]]:
    vectors: dict[str, list[float]] = {}
    max_member_count = max((len(group.member_ids) for group in dataset.groups.values()), default=1)

    for group in dataset.groups.values():
        updates: dict[str, float] = {
            "numeric:member_count_norm": len(group.member_ids) / max(max_member_count, 1),
            "numeric:is_group": 1.0,
        }
        updates.update(
            _one_hot_updates(
                feature_names,
                [
                    f"category:{group.category}",
                    f"primary_study_path:{group.primary_study_path}",
                ],
            )
        )
        for topic, weight in hin.group_topic_weights.get(group.id, {}).items():
            updates[f"topic:{topic}"] = weight
        vectors[group.id] = _vectorize(feature_names, updates)

    return vectors


def _message_vectors(
    dataset: SyntheticDataset,
    feature_names: list[str],
) -> dict[str, list[float]]:
    vectors: dict[str, list[float]] = {}
    max_length, max_reaction_count, max_day = _message_stats(dataset)

    for message in dataset.messages:
        updates = {
            "numeric:message_length_norm": len(message.text.split()) / max(max_length, 1),
            "numeric:reaction_count_norm": message.reaction_count / max(max_reaction_count, 1),
            "numeric:day_norm": message.day / max(max_day, 1),
            "numeric:is_message": 1.0,
        }
        vectors[message.id] = _vectorize(feature_names, updates)

    return vectors


def _topic_vectors(hin: HINGraph, feature_names: list[str]) -> dict[str, list[float]]:
    vectors: dict[str, list[float]] = {}
    for topic in hin.selected_topics:
        topic_id = f"topic:{topic}"
        updates = {
            "numeric:is_topic": 1.0,
            f"topic:{topic}": 1.0,
        }
        vectors[topic_id] = _vectorize(feature_names, updates)
    return vectors


def _build_adjacency(hin: HINGraph) -> tuple[dict[str, list[str]], dict[str, dict[str, list[str]]], dict[str, int]]:
    undirected_neighbors: dict[str, set[str]] = defaultdict(set)
    relation_neighbors: dict[str, dict[str, set[str]]] = defaultdict(lambda: defaultdict(set))
    relation_counts: Counter[str] = Counter()

    for edge in hin.edges:
        relation_counts[edge.relation] += 1
        undirected_neighbors[edge.source].add(edge.target)
        undirected_neighbors[edge.target].add(edge.source)
        relation_neighbors[edge.relation][edge.source].add(edge.target)
        relation_neighbors[edge.relation][edge.target].add(edge.source)

    adjacency = {node_id: sorted(neighbors) for node_id, neighbors in undirected_neighbors.items()}
    relation_adjacency = {
        relation: {
            node_id: sorted(neighbors)
            for node_id, neighbors in neighbors_by_node.items()
        }
        for relation, neighbors_by_node in relation_neighbors.items()
    }
    return adjacency, relation_adjacency, dict(relation_counts)


def _sparse_cosine_similarity(left: dict[str, float], right: dict[str, float]) -> float:
    if not left or not right:
        return 0.0

    shared = set(left) & set(right)
    numerator = sum(left[key] * right[key] for key in shared)
    left_norm = sum(value * value for value in left.values()) ** 0.5
    right_norm = sum(value * value for value in right.values()) ** 0.5
    if left_norm == 0.0 or right_norm == 0.0:
        return 0.0
    return numerator / (left_norm * right_norm)


def _hard_negative_score(
    dataset: SyntheticDataset,
    hin: HINGraph,
    student_id: str,
    group_id: str,
) -> float:
    student = dataset.students[student_id]
    group = dataset.groups[group_id]

    topic_similarity = _sparse_cosine_similarity(
        hin.student_topic_weights.get(student_id, {}),
        hin.group_topic_weights.get(group_id, {}),
    )
    study_path_match = 1.0 if group.primary_study_path == student.study_path else 0.0

    if group.member_ids:
        group_median = median(dataset.students[member_id].semester for member_id in group.member_ids)
        semester_proximity = 1.0 / (1.0 + abs(student.semester - group_median))
    else:
        semester_proximity = 0.0

    return (
        (0.65 * topic_similarity)
        + (0.2 * study_path_match)
        + (0.15 * semester_proximity)
    )


def _build_training_pairs(
    dataset: SyntheticDataset,
    hin: HINGraph,
    config: GraphSAGEConfig,
) -> tuple[list[tuple[str, str]], list[tuple[str, str]], list[tuple[str, str, str]], dict[str, int]]:
    rng = random.Random(config.seed)
    all_group_ids = list(dataset.groups.keys())
    positive_pairs: list[tuple[str, str]] = []
    negative_pairs: list[tuple[str, str]] = []
    training_triplets: list[tuple[str, str, str]] = []
    negative_pair_sources: Counter[str] = Counter()

    for student in dataset.students.values():
        joined_group_ids = set(student.joined_group_ids)
        if not joined_group_ids:
            continue

        negative_candidates = [group_id for group_id in all_group_ids if group_id not in joined_group_ids]
        if not negative_candidates:
            continue

        scored_candidates = sorted(
            negative_candidates,
            key=lambda group_id: (
                -_hard_negative_score(dataset, hin, student.id, group_id),
                group_id,
            ),
        )

        for group_id in joined_group_ids:
            positive_pairs.append((student.id, group_id))
            sample_count = min(config.negative_samples_per_positive, len(negative_candidates))
            if sample_count == 0:
                continue

            hard_pool_size = min(
                len(scored_candidates),
                max(sample_count, sample_count * config.hard_negative_pool_factor),
            )
            hard_pool = scored_candidates[:hard_pool_size]
            hard_sample_count = min(
                len(hard_pool),
                max(1, round(sample_count * config.hard_negative_ratio)),
            )

            hard_negative_ids = [
                group_id
                for group_id in rng.sample(hard_pool, k=hard_sample_count)
            ]
            remaining_candidates = [
                candidate
                for candidate in negative_candidates
                if candidate not in set(hard_negative_ids)
            ]
            random_sample_count = min(sample_count - len(hard_negative_ids), len(remaining_candidates))
            random_negative_ids = rng.sample(remaining_candidates, k=random_sample_count) if random_sample_count else []

            selected_negative_ids = hard_negative_ids + random_negative_ids
            if len(selected_negative_ids) < sample_count:
                fallback_candidates = [
                    candidate
                    for candidate in negative_candidates
                    if candidate not in set(selected_negative_ids)
                ]
                fallback_count = min(sample_count - len(selected_negative_ids), len(fallback_candidates))
                selected_negative_ids.extend(rng.sample(fallback_candidates, k=fallback_count))

            for negative_group_id in selected_negative_ids:
                negative_pairs.append((student.id, negative_group_id))
                training_triplets.append((student.id, group_id, negative_group_id))
                source = "hard" if negative_group_id in hard_negative_ids else "random"
                negative_pair_sources[source] += 1

    return positive_pairs, negative_pairs, training_triplets, dict(negative_pair_sources)


def _cold_start_split(
    dataset: SyntheticDataset,
    config: GraphSAGEConfig,
) -> tuple[list[str], list[str]]:
    sender_message_counts = _message_counts_by_sender(dataset)
    warm_student_ids: list[str] = []
    cold_student_ids: list[str] = []

    for student in dataset.students.values():
        interaction_count = len(student.joined_group_ids) + sender_message_counts.get(student.id, 0)
        if interaction_count < config.cold_start_interaction_threshold:
            cold_student_ids.append(student.id)
        else:
            warm_student_ids.append(student.id)

    if not warm_student_ids and cold_student_ids:
        warm_student_ids.append(cold_student_ids.pop(0))

    return sorted(warm_student_ids), sorted(cold_student_ids)


def prepare_graphsage_training_data(
    dataset: SyntheticDataset,
    hin: HINGraph,
    config: GraphSAGEConfig | None = None,
) -> GraphSAGEPreparedData:
    config = config or GraphSAGEConfig()
    feature_names = _build_feature_catalog(dataset, hin)

    node_features: dict[str, list[float]] = {}
    node_types = {node_id: node.node_type for node_id, node in hin.nodes.items()}
    node_features.update(_student_vectors(dataset, hin, feature_names))
    node_features.update(_group_vectors(dataset, hin, feature_names))
    node_features.update(_message_vectors(dataset, feature_names))
    node_features.update(_topic_vectors(hin, feature_names))

    adjacency, relation_adjacency, relation_counts = _build_adjacency(hin)
    positive_pairs, negative_pairs, training_triplets, negative_pair_sources = _build_training_pairs(dataset, hin, config)
    warm_student_ids, cold_student_ids = _cold_start_split(dataset, config)

    return GraphSAGEPreparedData(
        config=config,
        feature_names=feature_names,
        node_features=node_features,
        node_types=node_types,
        adjacency=adjacency,
        relation_adjacency=relation_adjacency,
        positive_pairs=positive_pairs,
        negative_pairs=negative_pairs,
        training_triplets=training_triplets,
        warm_student_ids=warm_student_ids,
        cold_student_ids=cold_student_ids,
        relation_counts=relation_counts,
        negative_pair_sources=negative_pair_sources,
        selected_topics=hin.selected_topics,
        torch_available=torch_available(),
    )
