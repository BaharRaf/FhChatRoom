from __future__ import annotations

import argparse
import json
from pathlib import Path

from recsys.baselines import build_detailed_recommendations
from recsys.baselines import build_firestore_payloads
from recsys.hin import build_hin
from recsys.models import DatasetConfig
from recsys.synthetic_data import generate_synthetic_dataset


def _write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the local BA2 recommender demo.")
    parser.add_argument("--students", type=int, default=120)
    parser.add_argument("--groups", type=int, default=24)
    parser.add_argument("--topics", type=int, default=30)
    parser.add_argument("--messages-per-day", type=int, default=250)
    parser.add_argument("--days", type=int, default=14)
    parser.add_argument("--top-k", type=int, default=10)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--output-dir", default="recsys/output/demo")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    config = DatasetConfig(
        num_students=args.students,
        num_groups=args.groups,
        num_topics=args.topics,
        messages_per_day=args.messages_per_day,
        num_days=args.days,
    )

    dataset = generate_synthetic_dataset(config=config, seed=args.seed)
    hin = build_hin(dataset=dataset, max_topics=args.topics)
    detailed_recommendations = build_detailed_recommendations(dataset=dataset, hin=hin, top_k=args.top_k)
    firestore_payloads = build_firestore_payloads(dataset=dataset, hin=hin, top_k=args.top_k)

    output_dir = Path(args.output_dir)
    _write_json(output_dir / "synthetic_dataset.json", dataset.to_dict())
    _write_json(output_dir / "hin_summary.json", hin.summary())
    _write_json(output_dir / "recommendations.json", detailed_recommendations)
    _write_json(output_dir / "firestore_payloads.json", firestore_payloads)

    sample_student_id = next(iter(dataset.students))
    sample_student = dataset.students[sample_student_id]
    sample_recommendations = detailed_recommendations[sample_student_id][:3]

    print(f"Generated dataset at {output_dir}")
    print(
        f"Students={len(dataset.students)} Groups={len(dataset.groups)} "
        f"Messages={len(dataset.messages)} Topics={len(hin.selected_topics)}"
    )
    print(
        f"Sample student: {sample_student.first_name} {sample_student.last_name} "
        f"({sample_student.study_path}, semester {sample_student.semester})"
    )
    print("Top recommendations:")
    for recommendation in sample_recommendations:
        print(
            f"- {recommendation['groupName']} "
            f"(score={recommendation['score']}, "
            f"topic={recommendation['breakdown']['topicSimilarity']}, "
            f"studyPath={recommendation['breakdown']['studyPathAffinity']})"
        )


if __name__ == "__main__":
    main()

