from __future__ import annotations

import math
import time
from dataclasses import asdict
from dataclasses import dataclass

import numpy as np

from recsys.graphsage_prep import GraphSAGEPreparedData
from recsys.models import SyntheticDataset


@dataclass(frozen=True)
class LightGCNConfig:
    embedding_dim: int = 16
    num_layers: int = 3
    learning_rate: float = 0.05
    epochs: int = 150
    weight_decay: float = 1e-4
    seed: int = 42


@dataclass
class LightGCNTrainingResult:
    config: LightGCNConfig
    losses: list[float]
    student_embeddings: dict[str, list[float]]
    group_embeddings: dict[str, list[float]]
    final_loss: float
    trained_at_ms: int

    def summary(self) -> dict[str, object]:
        return {
            "config": asdict(self.config),
            "epochs": len(self.losses),
            "initialLoss": self.losses[0] if self.losses else None,
            "finalLoss": self.final_loss,
            "numStudentEmbeddings": len(self.student_embeddings),
            "numGroupEmbeddings": len(self.group_embeddings),
            "trainedAtMs": self.trained_at_ms,
        }


def _build_interaction_matrices(
    prep: GraphSAGEPreparedData,
) -> tuple[list[str], list[str], dict[str, int], dict[str, int], np.ndarray, np.ndarray]:
    student_ids = sorted(
        node_id
        for node_id, node_type in prep.node_types.items()
        if node_type == "Student"
    )
    group_ids = sorted(
        node_id
        for node_id, node_type in prep.node_types.items()
        if node_type == "Group"
    )
    student_index = {student_id: index for index, student_id in enumerate(student_ids)}
    group_index = {group_id: index for index, group_id in enumerate(group_ids)}

    interaction_matrix = np.zeros((len(student_ids), len(group_ids)), dtype=np.float64)
    for student_id, group_id in prep.positive_pairs:
        if student_id in student_index and group_id in group_index:
            interaction_matrix[student_index[student_id], group_index[group_id]] = 1.0

    student_degree = interaction_matrix.sum(axis=1, keepdims=True)
    group_degree = interaction_matrix.sum(axis=0, keepdims=True)
    student_degree[student_degree == 0.0] = 1.0
    group_degree[group_degree == 0.0] = 1.0

    normalization = np.sqrt(student_degree * group_degree)
    normalized_student_to_group = interaction_matrix / normalization
    normalized_group_to_student = normalized_student_to_group.T

    return (
        student_ids,
        group_ids,
        student_index,
        group_index,
        normalized_student_to_group,
        normalized_group_to_student,
    )


def _triplet_indices(
    triplets: list[tuple[str, str, str]],
    student_index: dict[str, int],
    group_index: dict[str, int],
) -> np.ndarray:
    mapped = [
        (
            student_index[student_id],
            group_index[positive_group_id],
            group_index[negative_group_id],
        )
        for student_id, positive_group_id, negative_group_id in triplets
        if student_id in student_index
        and positive_group_id in group_index
        and negative_group_id in group_index
    ]
    return np.asarray(mapped, dtype=np.int64)


def _init_embeddings(rng: np.random.Generator, size: int, embedding_dim: int) -> np.ndarray:
    scale = math.sqrt(2.0 / max(embedding_dim, 1))
    return rng.normal(0.0, scale, size=(size, embedding_dim))


def _propagate(
    student_embeddings: np.ndarray,
    group_embeddings: np.ndarray,
    normalized_student_to_group: np.ndarray,
    normalized_group_to_student: np.ndarray,
    num_layers: int,
) -> tuple[list[np.ndarray], list[np.ndarray], np.ndarray, np.ndarray]:
    student_layers = [student_embeddings]
    group_layers = [group_embeddings]

    for _ in range(num_layers):
        next_student_embeddings = normalized_student_to_group @ group_layers[-1]
        next_group_embeddings = normalized_group_to_student @ student_layers[-1]
        student_layers.append(next_student_embeddings)
        group_layers.append(next_group_embeddings)

    final_student_embeddings = sum(student_layers) / len(student_layers)
    final_group_embeddings = sum(group_layers) / len(group_layers)
    return student_layers, group_layers, final_student_embeddings, final_group_embeddings


def _bpr_loss_and_gradients(
    student_embeddings: np.ndarray,
    group_embeddings: np.ndarray,
    triplet_indices: np.ndarray,
) -> tuple[float, np.ndarray, np.ndarray]:
    grad_students = np.zeros_like(student_embeddings)
    grad_groups = np.zeros_like(group_embeddings)
    if len(triplet_indices) == 0:
        return 0.0, grad_students, grad_groups

    student_indices = triplet_indices[:, 0]
    positive_indices = triplet_indices[:, 1]
    negative_indices = triplet_indices[:, 2]

    student_vectors = student_embeddings[student_indices]
    positive_vectors = group_embeddings[positive_indices]
    negative_vectors = group_embeddings[negative_indices]

    score_difference = np.sum(student_vectors * (positive_vectors - negative_vectors), axis=1)
    score_difference = np.clip(score_difference, -20.0, 20.0)
    probabilities = 1.0 / (1.0 + np.exp(-score_difference))
    safe_probabilities = np.clip(probabilities, 1e-9, 1.0)
    loss = float(-np.mean(np.log(safe_probabilities)))

    coefficients = ((probabilities - 1.0) / len(triplet_indices)).reshape(-1, 1)
    np.add.at(grad_students, student_indices, coefficients * (positive_vectors - negative_vectors))
    np.add.at(grad_groups, positive_indices, coefficients * student_vectors)
    np.add.at(grad_groups, negative_indices, -coefficients * student_vectors)

    return loss, grad_students, grad_groups


def _backward(
    student_layers: list[np.ndarray],
    group_layers: list[np.ndarray],
    grad_student_final: np.ndarray,
    grad_group_final: np.ndarray,
    normalized_student_to_group: np.ndarray,
    normalized_group_to_student: np.ndarray,
) -> tuple[np.ndarray, np.ndarray]:
    num_layers = len(student_layers) - 1
    averaging_factor = num_layers + 1
    grad_student_layers = [grad_student_final / averaging_factor for _ in range(averaging_factor)]
    grad_group_layers = [grad_group_final / averaging_factor for _ in range(averaging_factor)]

    for layer_index in reversed(range(1, averaging_factor)):
        grad_group_layers[layer_index - 1] += normalized_student_to_group.T @ grad_student_layers[layer_index]
        grad_student_layers[layer_index - 1] += normalized_group_to_student.T @ grad_group_layers[layer_index]

    return grad_student_layers[0], grad_group_layers[0]


def train_lightgcn_embeddings(
    prep: GraphSAGEPreparedData,
    config: LightGCNConfig | None = None,
) -> LightGCNTrainingResult:
    config = config or LightGCNConfig(seed=42)
    rng = np.random.default_rng(config.seed)

    (
        student_ids,
        group_ids,
        student_index,
        group_index,
        normalized_student_to_group,
        normalized_group_to_student,
    ) = _build_interaction_matrices(prep)
    triplet_indices = _triplet_indices(prep.training_triplets, student_index, group_index)

    student_embeddings = _init_embeddings(rng, len(student_ids), config.embedding_dim)
    group_embeddings = _init_embeddings(rng, len(group_ids), config.embedding_dim)

    losses: list[float] = []

    for _ in range(config.epochs):
        student_layers, group_layers, final_student_embeddings, final_group_embeddings = _propagate(
            student_embeddings=student_embeddings,
            group_embeddings=group_embeddings,
            normalized_student_to_group=normalized_student_to_group,
            normalized_group_to_student=normalized_group_to_student,
            num_layers=config.num_layers,
        )
        loss, grad_student_final, grad_group_final = _bpr_loss_and_gradients(
            student_embeddings=final_student_embeddings,
            group_embeddings=final_group_embeddings,
            triplet_indices=triplet_indices,
        )
        grad_student_initial, grad_group_initial = _backward(
            student_layers=student_layers,
            group_layers=group_layers,
            grad_student_final=grad_student_final,
            grad_group_final=grad_group_final,
            normalized_student_to_group=normalized_student_to_group,
            normalized_group_to_student=normalized_group_to_student,
        )
        grad_student_initial += config.weight_decay * student_embeddings
        grad_group_initial += config.weight_decay * group_embeddings
        student_embeddings -= config.learning_rate * grad_student_initial
        group_embeddings -= config.learning_rate * grad_group_initial
        losses.append(loss)

    _, _, final_student_embeddings, final_group_embeddings = _propagate(
        student_embeddings=student_embeddings,
        group_embeddings=group_embeddings,
        normalized_student_to_group=normalized_student_to_group,
        normalized_group_to_student=normalized_group_to_student,
        num_layers=config.num_layers,
    )

    return LightGCNTrainingResult(
        config=config,
        losses=losses,
        student_embeddings={
            student_id: final_student_embeddings[index].tolist()
            for index, student_id in enumerate(student_ids)
        },
        group_embeddings={
            group_id: final_group_embeddings[index].tolist()
            for index, group_id in enumerate(group_ids)
        },
        final_loss=losses[-1] if losses else 0.0,
        trained_at_ms=int(time.time() * 1000),
    )


def _popularity(dataset: SyntheticDataset, group_id: str) -> float:
    max_size = max((len(group.member_ids) for group in dataset.groups.values()), default=1)
    return len(dataset.groups[group_id].member_ids) / max(max_size, 1)


def build_lightgcn_recommendations(
    dataset: SyntheticDataset,
    training_result: LightGCNTrainingResult,
    top_k: int = 10,
) -> dict[str, list[dict[str, object]]]:
    recommendations: dict[str, list[dict[str, object]]] = {}

    for student_id, student in dataset.students.items():
        joined_group_ids = set(student.joined_group_ids)
        student_embedding = np.asarray(training_result.student_embeddings.get(student_id, []), dtype=np.float64)
        use_popularity_fallback = (
            len(joined_group_ids) == 0
            or student_embedding.size == 0
            or float(np.linalg.norm(student_embedding)) == 0.0
        )
        ranked: list[dict[str, object]] = []

        for group_id, group in dataset.groups.items():
            if group_id in joined_group_ids:
                continue

            popularity = _popularity(dataset, group_id)
            collaborative_score = 0.0
            if not use_popularity_fallback:
                group_embedding = np.asarray(training_result.group_embeddings.get(group_id, []), dtype=np.float64)
                if group_embedding.size:
                    collaborative_score = float(np.dot(student_embedding, group_embedding))

            score = popularity if use_popularity_fallback else collaborative_score
            ranked.append(
                {
                    "studentId": student_id,
                    "groupId": group_id,
                    "groupName": group.name,
                    "score": round(score, 6),
                    "collaborativeScore": round(collaborative_score, 6),
                    "popularity": round(popularity, 6),
                    "usedPopularityFallback": use_popularity_fallback,
                }
            )

        ranked.sort(
            key=lambda recommendation: (
                -float(recommendation["score"]),
                -float(recommendation["popularity"]),
                str(recommendation["groupName"]),
            )
        )
        recommendations[student_id] = ranked[:top_k]

    return recommendations


def build_lightgcn_firestore_payloads(
    dataset: SyntheticDataset,
    training_result: LightGCNTrainingResult,
    top_k: int = 10,
) -> dict[str, dict[str, object]]:
    recommendations = build_lightgcn_recommendations(dataset, training_result, top_k=top_k)
    return {
        student_id: {
            "recommendedRoomIds": [item["groupId"] for item in ranked],
            "recommendationsUpdatedAt": training_result.trained_at_ms,
            "recommendationSource": "LIGHT_GCN_LOCAL",
        }
        for student_id, ranked in recommendations.items()
    }
