from __future__ import annotations

import math
from dataclasses import dataclass
from statistics import mean

from recsys.models import SyntheticDataset


@dataclass(frozen=True)
class RankingMetrics:
    precision_at_k: float
    recall_at_k: float
    ndcg_at_k: float
    hit_rate_at_k: float
    mean_reciprocal_rank: float
    evaluated_users: int

    def to_dict(self) -> dict[str, float | int]:
        return {
            "precisionAtK": round(self.precision_at_k, 6),
            "recallAtK": round(self.recall_at_k, 6),
            "ndcgAtK": round(self.ndcg_at_k, 6),
            "hitRateAtK": round(self.hit_rate_at_k, 6),
            "mrr": round(self.mean_reciprocal_rank, 6),
            "evaluatedUsers": self.evaluated_users,
        }


def robustness_ratio(cold: RankingMetrics, warm: RankingMetrics) -> float:
    if warm.ndcg_at_k == 0.0:
        return 0.0
    return round(cold.ndcg_at_k / warm.ndcg_at_k, 6)


def _dcg_at_k(relevances: list[int], k: int) -> float:
    score = 0.0
    for index, relevance in enumerate(relevances[:k]):
        if relevance:
            score += relevance / math.log2(index + 2)
    return score


def evaluate_recommendations(
    dataset: SyntheticDataset,
    ranked_group_ids: dict[str, list[str]],
    held_out_group_ids: dict[str, list[str]],
    user_ids: list[str] | None = None,
    k: int = 10,
) -> RankingMetrics:
    candidate_user_ids = user_ids or sorted(held_out_group_ids.keys())
    precision_scores: list[float] = []
    recall_scores: list[float] = []
    ndcg_scores: list[float] = []
    hit_rate_scores: list[float] = []
    reciprocal_ranks: list[float] = []

    for user_id in candidate_user_ids:
        relevant = set(held_out_group_ids.get(user_id, []))
        if not relevant:
            continue

        ranked = ranked_group_ids.get(user_id, [])[:k]
        hits = [1 if group_id in relevant else 0 for group_id in ranked]
        hit_count = sum(hits)

        precision_scores.append(hit_count / max(len(ranked), 1))
        recall_scores.append(hit_count / len(relevant))
        hit_rate_scores.append(1.0 if hit_count > 0 else 0.0)
        ideal = [1] * min(len(relevant), k)
        ideal_dcg = _dcg_at_k(ideal, k)
        ndcg_scores.append(_dcg_at_k(hits, k) / ideal_dcg if ideal_dcg else 0.0)
        reciprocal_rank = 0.0
        for index, hit in enumerate(hits, start=1):
            if hit:
                reciprocal_rank = 1.0 / index
                break
        reciprocal_ranks.append(reciprocal_rank)

    if not precision_scores:
        return RankingMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0)

    return RankingMetrics(
        precision_at_k=mean(precision_scores),
        recall_at_k=mean(recall_scores),
        ndcg_at_k=mean(ndcg_scores),
        hit_rate_at_k=mean(hit_rate_scores),
        mean_reciprocal_rank=mean(reciprocal_ranks),
        evaluated_users=len(precision_scores),
    )


def compare_metrics(
    baseline: RankingMetrics,
    candidate: RankingMetrics,
) -> dict[str, float]:
    return {
        "precisionAtKDelta": round(candidate.precision_at_k - baseline.precision_at_k, 6),
        "recallAtKDelta": round(candidate.recall_at_k - baseline.recall_at_k, 6),
        "ndcgAtKDelta": round(candidate.ndcg_at_k - baseline.ndcg_at_k, 6),
        "hitRateAtKDelta": round(candidate.hit_rate_at_k - baseline.hit_rate_at_k, 6),
        "mrrDelta": round(candidate.mean_reciprocal_rank - baseline.mean_reciprocal_rank, 6),
    }


def build_leave_one_out_targets(dataset: SyntheticDataset) -> tuple[SyntheticDataset, dict[str, list[str]]]:
    held_out_group_ids: dict[str, list[str]] = {}

    for student in dataset.students.values():
        if len(student.joined_group_ids) <= 1:
            continue
        held_out_group_id = student.joined_group_ids.pop()
        held_out_group_ids[student.id] = [held_out_group_id]
        if student.id in dataset.groups[held_out_group_id].member_ids:
            dataset.groups[held_out_group_id].member_ids.remove(student.id)

    held_out_pairs = {
        (student_id, group_ids[0])
        for student_id, group_ids in held_out_group_ids.items()
        if group_ids
    }
    dataset.messages = [
        message
        for message in dataset.messages
        if (message.sender_id, message.group_id) not in held_out_pairs
    ]

    return dataset, held_out_group_ids
