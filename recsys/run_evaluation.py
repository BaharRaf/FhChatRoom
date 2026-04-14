from __future__ import annotations

import argparse
import json
from copy import deepcopy
from pathlib import Path

from recsys.baselines import build_detailed_recommendations
from recsys.evaluation import build_leave_one_out_targets
from recsys.evaluation import compare_metrics
from recsys.evaluation import evaluate_recommendations
from recsys.evaluation import robustness_ratio
from recsys.firestore_json_adapter import dataset_from_firestore_json
from recsys.graphsage_prep import GraphSAGEConfig
from recsys.graphsage_prep import prepare_graphsage_training_data
from recsys.graphsage_train import GraphSAGETrainConfig
from recsys.graphsage_train import build_graphsage_recommendations
from recsys.graphsage_train import train_graphsage_embeddings
from recsys.hin import build_hin
from recsys.lightgcn_train import LightGCNConfig
from recsys.lightgcn_train import build_lightgcn_recommendations
from recsys.lightgcn_train import train_lightgcn_embeddings
from recsys.models import DatasetConfig
from recsys.synthetic_data import generate_synthetic_dataset


def _write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate baseline vs local GraphSAGE-style vs LightGCN recommendations.")
    parser.add_argument("--mode", choices=["synthetic", "json"], default="synthetic")
    parser.add_argument("--students", type=int, default=120)
    parser.add_argument("--groups", type=int, default=24)
    parser.add_argument("--topics", type=int, default=30)
    parser.add_argument("--messages-per-day", type=int, default=250)
    parser.add_argument("--days", type=int, default=14)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--epochs", type=int, default=120)
    parser.add_argument("--learning-rate", type=float, default=0.05)
    parser.add_argument("--graphsage-epochs", type=int)
    parser.add_argument("--graphsage-learning-rate", type=float)
    parser.add_argument("--lightgcn-epochs", type=int)
    parser.add_argument("--lightgcn-learning-rate", type=float)
    parser.add_argument("--negative-samples-per-positive", type=int, default=3)
    parser.add_argument("--hard-negative-ratio", type=float, default=0.67)
    parser.add_argument("--embedding-dim", type=int, default=16)
    parser.add_argument("--lightgcn-layers", type=int, default=3)
    parser.add_argument("--top-k", type=int, default=10)
    parser.add_argument("--users")
    parser.add_argument("--rooms")
    parser.add_argument("--messages")
    parser.add_argument("--output-dir", required=True)
    return parser.parse_args()


def _load_dataset(args: argparse.Namespace):
    if args.mode == "json":
        if not args.users or not args.rooms or not args.messages:
            raise SystemExit("--users, --rooms, and --messages are required in json mode")
        return dataset_from_firestore_json(
            users_path=args.users,
            rooms_path=args.rooms,
            messages_path=args.messages,
            seed=args.seed,
        )

    config = DatasetConfig(
        num_students=args.students,
        num_groups=args.groups,
        num_topics=args.topics,
        messages_per_day=args.messages_per_day,
        num_days=args.days,
    )
    return generate_synthetic_dataset(config=config, seed=args.seed)


def main() -> None:
    args = parse_args()
    dataset = _load_dataset(args)
    train_dataset, held_out = build_leave_one_out_targets(deepcopy(dataset))
    hin = build_hin(train_dataset, max_topics=args.topics)
    graphsage_epochs = args.graphsage_epochs or args.epochs
    graphsage_learning_rate = args.graphsage_learning_rate or args.learning_rate
    lightgcn_epochs = args.lightgcn_epochs or args.epochs
    lightgcn_learning_rate = args.lightgcn_learning_rate or args.learning_rate

    baseline_ranked = {
        student_id: [item["groupId"] for item in ranked]
        for student_id, ranked in build_detailed_recommendations(train_dataset, hin, top_k=args.top_k).items()
    }

    prep = prepare_graphsage_training_data(
        dataset=train_dataset,
        hin=hin,
        config=GraphSAGEConfig(
            seed=args.seed,
            negative_samples_per_positive=args.negative_samples_per_positive,
            hard_negative_ratio=args.hard_negative_ratio,
        ),
    )
    result = train_graphsage_embeddings(
        prep=prep,
        config=GraphSAGETrainConfig(
            embedding_dim=args.embedding_dim,
            learning_rate=graphsage_learning_rate,
            epochs=graphsage_epochs,
            seed=args.seed,
        ),
    )
    graphsage_ranked = {
        student_id: [item["groupId"] for item in ranked]
        for student_id, ranked in build_graphsage_recommendations(train_dataset, hin, result, top_k=args.top_k).items()
    }
    lightgcn_result = train_lightgcn_embeddings(
        prep=prep,
        config=LightGCNConfig(
            embedding_dim=args.embedding_dim,
            num_layers=args.lightgcn_layers,
            learning_rate=lightgcn_learning_rate,
            epochs=lightgcn_epochs,
            seed=args.seed,
        ),
    )
    lightgcn_ranked = {
        student_id: [item["groupId"] for item in ranked]
        for student_id, ranked in build_lightgcn_recommendations(train_dataset, lightgcn_result, top_k=args.top_k).items()
    }

    baseline_all = evaluate_recommendations(train_dataset, baseline_ranked, held_out, k=args.top_k)
    baseline_warm = evaluate_recommendations(train_dataset, baseline_ranked, held_out, user_ids=prep.warm_student_ids, k=args.top_k)
    baseline_cold = evaluate_recommendations(train_dataset, baseline_ranked, held_out, user_ids=prep.cold_student_ids, k=args.top_k)
    graphsage_all = evaluate_recommendations(train_dataset, graphsage_ranked, held_out, k=args.top_k)
    graphsage_warm = evaluate_recommendations(train_dataset, graphsage_ranked, held_out, user_ids=prep.warm_student_ids, k=args.top_k)
    graphsage_cold = evaluate_recommendations(train_dataset, graphsage_ranked, held_out, user_ids=prep.cold_student_ids, k=args.top_k)
    lightgcn_all = evaluate_recommendations(train_dataset, lightgcn_ranked, held_out, k=args.top_k)
    lightgcn_warm = evaluate_recommendations(train_dataset, lightgcn_ranked, held_out, user_ids=prep.warm_student_ids, k=args.top_k)
    lightgcn_cold = evaluate_recommendations(train_dataset, lightgcn_ranked, held_out, user_ids=prep.cold_student_ids, k=args.top_k)

    report = {
        "evaluationProtocol": "leave_one_out_membership_holdout",
        "baseline": {
            "all": baseline_all.to_dict(),
            "warm": baseline_warm.to_dict(),
            "cold": baseline_cold.to_dict(),
            "coldStartRobustnessNdcgAtK": robustness_ratio(baseline_cold, baseline_warm),
        },
        "graphsageLocal": {
            "all": graphsage_all.to_dict(),
            "warm": graphsage_warm.to_dict(),
            "cold": graphsage_cold.to_dict(),
            "coldStartRobustnessNdcgAtK": robustness_ratio(graphsage_cold, graphsage_warm),
        },
        "lightgcnLocal": {
            "all": lightgcn_all.to_dict(),
            "warm": lightgcn_warm.to_dict(),
            "cold": lightgcn_cold.to_dict(),
            "coldStartRobustnessNdcgAtK": robustness_ratio(lightgcn_cold, lightgcn_warm),
        },
        "graphsageDeltaVsBaseline": {
            "all": compare_metrics(baseline_all, graphsage_all),
            "warm": compare_metrics(baseline_warm, graphsage_warm),
            "cold": compare_metrics(baseline_cold, graphsage_cold),
        },
        "lightgcnDeltaVsBaseline": {
            "all": compare_metrics(baseline_all, lightgcn_all),
            "warm": compare_metrics(baseline_warm, lightgcn_warm),
            "cold": compare_metrics(baseline_cold, lightgcn_cold),
        },
        "graphsageDeltaVsLightGCN": {
            "all": compare_metrics(lightgcn_all, graphsage_all),
            "warm": compare_metrics(lightgcn_warm, graphsage_warm),
            "cold": compare_metrics(lightgcn_cold, graphsage_cold),
        },
        "segments": {
            "warmStudents": len(prep.warm_student_ids),
            "coldStudents": len(prep.cold_student_ids),
        },
        "trainingData": {
            "positivePairs": len(prep.positive_pairs),
            "negativePairs": len(prep.negative_pairs),
            "trainingTriplets": len(prep.training_triplets),
            "negativePairSources": prep.negative_pair_sources,
        },
        "modelSummaries": {
            "graphsage": result.summary(),
            "lightgcn": lightgcn_result.summary(),
        },
        "heldOutUsers": len(held_out),
    }

    output_dir = Path(args.output_dir)
    _write_json(output_dir / "evaluation_report.json", report)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
