from __future__ import annotations

import time
from statistics import median

from recsys.models import HINGraph
from recsys.models import Recommendation
from recsys.models import RecommendationBreakdown
from recsys.models import SyntheticDataset


def _cosine_similarity(left: dict[str, float], right: dict[str, float]) -> float:
    if not left or not right:
        return 0.0

    shared_keys = set(left) & set(right)
    numerator = sum(left[key] * right[key] for key in shared_keys)
    left_norm = sum(value * value for value in left.values()) ** 0.5
    right_norm = sum(value * value for value in right.values()) ** 0.5

    if left_norm == 0 or right_norm == 0:
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
        return 0.0

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
    return len(dataset.groups[group_id].member_ids) / max_size


def recommend_groups_for_student(
    dataset: SyntheticDataset,
    hin: HINGraph,
    student_id: str,
    top_k: int = 10,
    lambda_relevance: float = 0.8,
) -> list[Recommendation]:
    student = dataset.students[student_id]
    joined_group_ids = set(student.joined_group_ids)
    peer_ids = _peer_set(dataset, student_id)

    recommendations: list[Recommendation] = []

    for group_id, group in dataset.groups.items():
        if group_id in joined_group_ids:
            continue

        topic_similarity = _cosine_similarity(
            hin.student_topic_weights.get(student_id, {}),
            hin.group_topic_weights.get(group_id, {}),
        )
        study_path_affinity = _study_path_affinity(dataset, student_id, group_id)
        semester_proximity = _semester_proximity(dataset, student_id, group_id)
        popularity = _popularity(dataset, group_id)
        member_similarity = _jaccard_similarity(peer_ids, set(group.member_ids))
        serendipity = topic_similarity * (1.0 - member_similarity)
        relevance = (
            0.6 * topic_similarity
            + 0.25 * study_path_affinity
            + 0.15 * semester_proximity
        )
        score = (lambda_relevance * relevance) + ((1.0 - lambda_relevance) * serendipity) + (0.1 * popularity)

        recommendations.append(
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

    recommendations.sort(key=lambda recommendation: (-recommendation.score, recommendation.group_name))
    return recommendations[:top_k]


def build_firestore_payloads(
    dataset: SyntheticDataset,
    hin: HINGraph,
    top_k: int = 10,
    recommendation_source: str = "CONTENT_BASED",
) -> dict[str, dict[str, object]]:
    generated_at = int(time.time() * 1000)
    payloads: dict[str, dict[str, object]] = {}

    for student_id in dataset.students:
        recommendations = recommend_groups_for_student(
            dataset=dataset,
            hin=hin,
            student_id=student_id,
            top_k=top_k,
        )
        payloads[student_id] = {
            "recommendedRoomIds": [recommendation.group_id for recommendation in recommendations],
            "recommendationsUpdatedAt": generated_at,
            "recommendationSource": recommendation_source,
        }

    return payloads


def build_detailed_recommendations(
    dataset: SyntheticDataset,
    hin: HINGraph,
    top_k: int = 10,
) -> dict[str, list[dict[str, object]]]:
    detailed: dict[str, list[dict[str, object]]] = {}

    for student_id in dataset.students:
        detailed[student_id] = [
            recommendation.to_dict()
            for recommendation in recommend_groups_for_student(
                dataset=dataset,
                hin=hin,
                student_id=student_id,
                top_k=top_k,
            )
        ]

    return detailed

