from __future__ import annotations

import math
import time
from dataclasses import asdict
from dataclasses import dataclass
from dataclasses import field
from statistics import median

import numpy as np

from recsys.graphsage_prep import GraphSAGEPreparedData
from recsys.models import HINGraph
from recsys.models import Recommendation
from recsys.models import RecommendationBreakdown
from recsys.models import SyntheticDataset


@dataclass(frozen=True)
class GraphSAGETrainConfig:
    hidden_dim: int = 32
    embedding_dim: int = 16
    num_layers: int = 2
    learning_rate: float = 0.05
    epochs: int = 150
    weight_decay: float = 1e-4
    self_loop_weight: float = 0.35
    relation_weights: dict[str, float] = field(
        default_factory=lambda: {
            "MEMBER_OF": 1.6,
            "INTERESTED_IN": 1.35,
            "RELATED_TO": 1.35,
            "SENDS": 0.85,
            "POSTED_IN": 0.75,
            "CONTAINS": 0.75,
        }
    )
    seed: int = 42


@dataclass
class GraphSAGETrainingResult:
    config: GraphSAGETrainConfig
    losses: list[float]
    node_order: list[str]
    embeddings: dict[str, list[float]]
    final_loss: float
    trained_at_ms: int

    def summary(self) -> dict[str, object]:
        return {
            "config": asdict(self.config),
            "epochs": len(self.losses),
            "initialLoss": self.losses[0] if self.losses else None,
            "finalLoss": self.final_loss,
            "numEmbeddings": len(self.embeddings),
            "trainedAtMs": self.trained_at_ms,
        }


def _normalize_rows(matrix: np.ndarray) -> np.ndarray:
    norms = np.linalg.norm(matrix, axis=1, keepdims=True)
    norms[norms == 0.0] = 1.0
    return matrix / norms


def _normalize_adjacency(matrix: np.ndarray) -> np.ndarray:
    degree = matrix.sum(axis=1, keepdims=True)
    degree[degree == 0.0] = 1.0
    return matrix / degree


def _build_relation_matrix(
    neighbors_by_node: dict[str, list[str]],
    node_index: dict[str, int],
    size: int,
) -> np.ndarray:
    matrix = np.zeros((size, size), dtype=np.float64)
    for source_id, neighbors in neighbors_by_node.items():
        if source_id not in node_index:
            continue
        source_index = node_index[source_id]
        for neighbor_id in neighbors:
            if neighbor_id in node_index:
                matrix[source_index, node_index[neighbor_id]] = 1.0
    return _normalize_adjacency(matrix)


def _build_matrices(
    prep: GraphSAGEPreparedData,
    config: GraphSAGETrainConfig,
) -> tuple[list[str], dict[str, int], np.ndarray, np.ndarray]:
    node_order = sorted(prep.node_features.keys())
    node_index = {node_id: index for index, node_id in enumerate(node_order)}
    features = np.asarray([prep.node_features[node_id] for node_id in node_order], dtype=np.float64)
    features = _normalize_rows(features)

    adjacency = np.eye(len(node_order), dtype=np.float64) * config.self_loop_weight
    for relation, neighbors_by_node in prep.relation_adjacency.items():
        relation_weight = config.relation_weights.get(relation, 1.0)
        if relation_weight <= 0.0:
            continue
        adjacency += relation_weight * _build_relation_matrix(neighbors_by_node, node_index, len(node_order))

    adjacency = _normalize_adjacency(adjacency)
    return node_order, node_index, features, adjacency


def _triplet_indices(triplets: list[tuple[str, str, str]], node_index: dict[str, int]) -> np.ndarray:
    mapped = [
        (node_index[student_id], node_index[positive_group_id], node_index[negative_group_id])
        for student_id, positive_group_id, negative_group_id in triplets
        if student_id in node_index and positive_group_id in node_index and negative_group_id in node_index
    ]
    return np.asarray(mapped, dtype=np.int64)


def _init_weight(rng: np.random.Generator, input_dim: int, output_dim: int) -> np.ndarray:
    scale = math.sqrt(2.0 / max(input_dim + output_dim, 1))
    return rng.normal(0.0, scale, size=(input_dim, output_dim))


def _forward(features: np.ndarray, adjacency: np.ndarray, weights: list[np.ndarray]) -> tuple[list[dict[str, np.ndarray]], np.ndarray]:
    current = features
    caches: list[dict[str, np.ndarray]] = []

    for weight in weights:
        previous = current
        neighbor_mean = adjacency @ previous
        layer_input = np.concatenate([previous, neighbor_mean], axis=1)
        pre_activation = layer_input @ weight
        current = np.tanh(pre_activation)
        caches.append(
            {
                "previous": previous,
                "input": layer_input,
                "pre_activation": pre_activation,
            }
        )

    embeddings = _normalize_rows(current)
    return caches, embeddings


def _ranking_loss_and_gradient(
    embeddings: np.ndarray,
    triplet_indices: np.ndarray,
) -> tuple[float, np.ndarray]:
    grad_embeddings = np.zeros_like(embeddings)
    if len(triplet_indices) == 0:
        return 0.0, grad_embeddings

    loss = 0.0

    for student_index, positive_group_index, negative_group_index in triplet_indices:
        student_embedding = embeddings[student_index]
        positive_group_embedding = embeddings[positive_group_index]
        negative_group_embedding = embeddings[negative_group_index]
        score_difference = float(
            np.dot(student_embedding, positive_group_embedding)
            - np.dot(student_embedding, negative_group_embedding)
        )
        score_difference = max(min(score_difference, 20.0), -20.0)
        probability = 1.0 / (1.0 + math.exp(-score_difference))
        coeff = probability - 1.0
        loss += -math.log(max(probability, 1e-9))
        grad_embeddings[student_index] += coeff * (positive_group_embedding - negative_group_embedding)
        grad_embeddings[positive_group_index] += coeff * student_embedding
        grad_embeddings[negative_group_index] -= coeff * student_embedding

    grad_embeddings /= len(triplet_indices)
    loss /= len(triplet_indices)
    return loss, grad_embeddings


def _backward(
    features: np.ndarray,
    adjacency: np.ndarray,
    weights: list[np.ndarray],
    caches: list[dict[str, np.ndarray]],
    grad_output: np.ndarray,
    weight_decay: float,
) -> list[np.ndarray]:
    gradients = [np.zeros_like(weight) for weight in weights]
    current_grad = grad_output

    for layer_index in reversed(range(len(weights))):
        weight = weights[layer_index]
        previous = features if layer_index == 0 else np.tanh(caches[layer_index - 1]["pre_activation"])
        pre_activation = caches[layer_index]["pre_activation"]
        layer_input = caches[layer_index]["input"]

        activation_grad = current_grad * (1.0 - np.tanh(pre_activation) ** 2)
        gradients[layer_index] = (layer_input.T @ activation_grad) / max(layer_input.shape[0], 1)
        gradients[layer_index] += weight_decay * weight

        grad_input = activation_grad @ weight.T
        previous_dim = previous.shape[1]
        grad_self = grad_input[:, :previous_dim]
        grad_neighbors = grad_input[:, previous_dim:]
        current_grad = grad_self + adjacency.T @ grad_neighbors

    return gradients


def train_graphsage_embeddings(
    prep: GraphSAGEPreparedData,
    config: GraphSAGETrainConfig | None = None,
) -> GraphSAGETrainingResult:
    config = config or GraphSAGETrainConfig(seed=42)
    rng = np.random.default_rng(config.seed)

    node_order, node_index, features, adjacency = _build_matrices(prep, config)
    triplet_indices = _triplet_indices(prep.training_triplets, node_index)

    weights: list[np.ndarray] = []
    dimensions = [config.hidden_dim] * max(config.num_layers - 1, 0) + [config.embedding_dim]
    current_dim = features.shape[1]
    for output_dim in dimensions:
        weights.append(_init_weight(rng, current_dim * 2, output_dim))
        current_dim = output_dim

    losses: list[float] = []

    for _ in range(config.epochs):
        caches, embeddings = _forward(features, adjacency, weights)
        loss, grad_embeddings = _ranking_loss_and_gradient(embeddings, triplet_indices)
        gradients = _backward(
            features=features,
            adjacency=adjacency,
            weights=weights,
            caches=caches,
            grad_output=grad_embeddings,
            weight_decay=config.weight_decay,
        )
        for index, gradient in enumerate(gradients):
            weights[index] -= config.learning_rate * gradient
        losses.append(loss)

    _, final_embeddings = _forward(features, adjacency, weights)
    embedding_map = {
        node_id: final_embeddings[index].tolist()
        for index, node_id in enumerate(node_order)
    }

    return GraphSAGETrainingResult(
        config=config,
        losses=losses,
        node_order=node_order,
        embeddings=embedding_map,
        final_loss=losses[-1] if losses else 0.0,
        trained_at_ms=int(time.time() * 1000),
    )


def _cosine_similarity(left: np.ndarray, right: np.ndarray) -> float:
    denominator = np.linalg.norm(left) * np.linalg.norm(right)
    if denominator == 0.0:
        return 0.0
    return float(np.dot(left, right) / denominator)


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


def _jaccard_similarity(left: set[str], right: set[str]) -> float:
    union = left | right
    if not union:
        return 0.0
    return len(left & right) / len(union)


def _peer_set(dataset: SyntheticDataset, student_id: str) -> set[str]:
    peer_ids: set[str] = set()
    student = dataset.students[student_id]
    for group_id in student.joined_group_ids:
        peer_ids.update(dataset.groups[group_id].member_ids)
    peer_ids.discard(student_id)
    return peer_ids


def _study_path_affinity(dataset: SyntheticDataset, student_id: str, group_id: str) -> float:
    student = dataset.students[student_id]
    group = dataset.groups[group_id]
    if not group.member_ids:
        return 1.0 if group.primary_study_path == student.study_path else 0.0
    matching_members = sum(
        1
        for member_id in group.member_ids
        if dataset.students[member_id].study_path == student.study_path
    )
    return matching_members / len(group.member_ids)


def _semester_proximity(dataset: SyntheticDataset, student_id: str, group_id: str) -> float:
    student = dataset.students[student_id]
    group = dataset.groups[group_id]
    if not group.member_ids:
        return 0.0
    group_median = median(dataset.students[member_id].semester for member_id in group.member_ids)
    return 1.0 / (1.0 + abs(student.semester - group_median))


def _popularity(dataset: SyntheticDataset, group_id: str) -> float:
    max_size = max((len(group.member_ids) for group in dataset.groups.values()), default=1)
    return len(dataset.groups[group_id].member_ids) / max(max_size, 1)


def build_graphsage_recommendations(
    dataset: SyntheticDataset,
    hin: HINGraph,
    training_result: GraphSAGETrainingResult,
    top_k: int = 10,
) -> dict[str, list[dict[str, object]]]:
    recommendations: dict[str, list[dict[str, object]]] = {}

    for student_id, student in dataset.students.items():
        student_embedding = np.asarray(training_result.embeddings.get(student_id, []), dtype=np.float64)
        if student_embedding.size == 0:
            recommendations[student_id] = []
            continue

        joined_group_ids = set(student.joined_group_ids)
        peer_ids = _peer_set(dataset, student_id)
        ranked: list[Recommendation] = []

        for group_id, group in dataset.groups.items():
            if group_id in joined_group_ids:
                continue
            group_embedding = np.asarray(training_result.embeddings.get(group_id, []), dtype=np.float64)
            if group_embedding.size == 0:
                continue
            embedding_similarity = _cosine_similarity(student_embedding, group_embedding)
            topic_similarity = _sparse_cosine_similarity(
                hin.student_topic_weights.get(student_id, {}),
                hin.group_topic_weights.get(group_id, {}),
            )
            study_path_affinity = _study_path_affinity(dataset, student_id, group_id)
            semester_proximity = _semester_proximity(dataset, student_id, group_id)
            popularity = _popularity(dataset, group_id)
            member_similarity = _jaccard_similarity(peer_ids, set(group.member_ids))
            serendipity = topic_similarity * (1.0 - member_similarity)
            relevance = (
                (0.65 * embedding_similarity)
                + (0.2 * topic_similarity)
                + (0.1 * study_path_affinity)
                + (0.05 * semester_proximity)
            )
            score = (
                (0.8 * relevance)
                + (0.15 * serendipity)
                + (0.05 * popularity)
            )
            ranked.append(
                Recommendation(
                    student_id=student_id,
                    group_id=group_id,
                    group_name=group.name,
                    score=score,
                    breakdown=RecommendationBreakdown(
                        topic_similarity=topic_similarity,
                        study_path_affinity=study_path_affinity,
                        semester_proximity=semester_proximity,
                        popularity=popularity,
                        serendipity=serendipity,
                        relevance=relevance,
                    ),
                )
            )

        ranked.sort(key=lambda recommendation: (-recommendation.score, recommendation.group_name))
        recommendations[student_id] = [recommendation.to_dict() for recommendation in ranked[:top_k]]

    return recommendations


def build_graphsage_firestore_payloads(
    dataset: SyntheticDataset,
    hin: HINGraph,
    training_result: GraphSAGETrainingResult,
    top_k: int = 10,
) -> dict[str, dict[str, object]]:
    recommendations = build_graphsage_recommendations(dataset, hin, training_result, top_k=top_k)
    return {
        student_id: {
            "recommendedRoomIds": [item["groupId"] for item in ranked],
            "recommendationsUpdatedAt": training_result.trained_at_ms,
            "recommendationSource": "GRAPH_SAGE_LOCAL",
        }
        for student_id, ranked in recommendations.items()
    }
