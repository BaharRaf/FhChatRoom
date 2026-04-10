from __future__ import annotations

import argparse
import json
from pathlib import Path

from recsys.firestore_json_adapter import dataset_from_firestore_json
from recsys.graphsage_prep import GraphSAGEConfig
from recsys.graphsage_prep import prepare_graphsage_training_data
from recsys.graphsage_train import GraphSAGETrainConfig
from recsys.graphsage_train import build_graphsage_firestore_payloads
from recsys.graphsage_train import build_graphsage_recommendations
from recsys.graphsage_train import train_graphsage_embeddings
from recsys.hin import build_hin
from recsys.models import DatasetConfig
from recsys.synthetic_data import generate_synthetic_dataset


def _write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train a local GraphSAGE-style recommender.")
    parser.add_argument("--mode", choices=["synthetic", "json"], default="synthetic")
    parser.add_argument("--students", type=int, default=120)
    parser.add_argument("--groups", type=int, default=24)
    parser.add_argument("--topics", type=int, default=30)
    parser.add_argument("--messages-per-day", type=int, default=250)
    parser.add_argument("--days", type=int, default=14)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--epochs", type=int, default=150)
    parser.add_argument("--learning-rate", type=float, default=0.05)
    parser.add_argument("--negative-samples-per-positive", type=int, default=3)
    parser.add_argument("--hard-negative-ratio", type=float, default=0.67)
    parser.add_argument("--hidden-dim", type=int, default=32)
    parser.add_argument("--embedding-dim", type=int, default=16)
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
    hin = build_hin(dataset=dataset, max_topics=args.topics)
    prep = prepare_graphsage_training_data(
        dataset=dataset,
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
            hidden_dim=args.hidden_dim,
            embedding_dim=args.embedding_dim,
            learning_rate=args.learning_rate,
            epochs=args.epochs,
            seed=args.seed,
        ),
    )

    recommendations = build_graphsage_recommendations(
        dataset=dataset,
        hin=hin,
        training_result=result,
        top_k=args.top_k,
    )
    payloads = build_graphsage_firestore_payloads(
        dataset=dataset,
        hin=hin,
        training_result=result,
        top_k=args.top_k,
    )

    output_dir = Path(args.output_dir)
    _write_json(output_dir / "graphsage_prep_summary.json", prep.summary())
    _write_json(output_dir / "graphsage_training_summary.json", result.summary())
    _write_json(output_dir / "graphsage_losses.json", result.losses)
    _write_json(output_dir / "graphsage_embeddings.json", result.embeddings)
    _write_json(output_dir / "graphsage_recommendations.json", recommendations)
    _write_json(output_dir / "graphsage_firestore_payloads.json", payloads)

    print(f"Trained local GraphSAGE-style model in {output_dir}")
    print(
        f"epochs={len(result.losses)} "
        f"final_loss={result.final_loss:.6f} "
        f"embeddings={len(result.embeddings)}"
    )


if __name__ == "__main__":
    main()
