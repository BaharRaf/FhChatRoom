from __future__ import annotations

import argparse
import json
from pathlib import Path

from recsys.firestore_json_adapter import dataset_from_firestore_json
from recsys.graphsage_prep import GraphSAGEConfig
from recsys.graphsage_prep import prepare_graphsage_training_data
from recsys.hin import build_hin
from recsys.models import DatasetConfig
from recsys.synthetic_data import generate_synthetic_dataset


def _write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Prepare local GraphSAGE training artifacts.")
    parser.add_argument("--mode", choices=["synthetic", "json"], default="synthetic")
    parser.add_argument("--students", type=int, default=120)
    parser.add_argument("--groups", type=int, default=24)
    parser.add_argument("--topics", type=int, default=30)
    parser.add_argument("--messages-per-day", type=int, default=250)
    parser.add_argument("--days", type=int, default=14)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--top-k-topics", type=int, default=50)
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
    hin = build_hin(dataset=dataset, max_topics=args.top_k_topics)
    prep = prepare_graphsage_training_data(
        dataset=dataset,
        hin=hin,
        config=GraphSAGEConfig(seed=args.seed),
    )

    output_dir = Path(args.output_dir)
    _write_json(output_dir / "graphsage_prep_summary.json", prep.summary())
    _write_json(output_dir / "graphsage_feature_names.json", prep.feature_names)
    _write_json(output_dir / "graphsage_positive_pairs.json", prep.positive_pairs)
    _write_json(output_dir / "graphsage_negative_pairs.json", prep.negative_pairs)
    _write_json(
        output_dir / "graphsage_cold_start_split.json",
        {
            "warmStudentIds": prep.warm_student_ids,
            "coldStudentIds": prep.cold_student_ids,
        },
    )

    print(f"Prepared GraphSAGE artifacts in {output_dir}")
    print(
        f"feature_dim={len(prep.feature_names)} "
        f"positive_pairs={len(prep.positive_pairs)} "
        f"negative_pairs={len(prep.negative_pairs)} "
        f"warm={len(prep.warm_student_ids)} cold={len(prep.cold_student_ids)} "
        f"torch_available={prep.torch_available}"
    )


if __name__ == "__main__":
    main()
