from __future__ import annotations

import argparse
import json
from pathlib import Path

from recsys.baselines import build_detailed_recommendations
from recsys.baselines import build_firestore_payloads
from recsys.firestore_json_adapter import dataset_from_firestore_json
from recsys.hin import build_hin


def _write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the local BA2 recommender on exported JSON data.")
    parser.add_argument("--users", required=True, help="Path to users JSON export")
    parser.add_argument("--rooms", required=True, help="Path to rooms JSON export")
    parser.add_argument("--messages", required=True, help="Path to messages JSON export")
    parser.add_argument("--topics", type=int, default=50)
    parser.add_argument("--top-k", type=int, default=10)
    parser.add_argument("--output-dir", required=True)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    dataset = dataset_from_firestore_json(
        users_path=args.users,
        rooms_path=args.rooms,
        messages_path=args.messages,
    )
    hin = build_hin(dataset=dataset, max_topics=args.topics)
    detailed_recommendations = build_detailed_recommendations(dataset=dataset, hin=hin, top_k=args.top_k)
    firestore_payloads = build_firestore_payloads(dataset=dataset, hin=hin, top_k=args.top_k)

    output_dir = Path(args.output_dir)
    _write_json(output_dir / "snapshot_dataset.json", dataset.to_dict())
    _write_json(output_dir / "hin_summary.json", hin.summary())
    _write_json(output_dir / "recommendations.json", detailed_recommendations)
    _write_json(output_dir / "firestore_payloads.json", firestore_payloads)

    print(f"Processed exported JSON into {output_dir}")
    print(
        f"Students={len(dataset.students)} Groups={len(dataset.groups)} "
        f"Messages={len(dataset.messages)} Topics={len(hin.selected_topics)}"
    )


if __name__ == "__main__":
    main()

