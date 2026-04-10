# Local Recsys Demo

This package is the first zero-cost BA2 recommender slice. It runs entirely on your machine and does not call Firebase, paid APIs, or cloud GPUs.

## What it does

- generates a synthetic FH-like collaboration dataset
- constructs a typed HIN with:
  - `Student`
  - `Group`
  - `Message`
  - `Topic`
- creates the thesis-style relations:
  - `MEMBER_OF`
  - `INTERESTED_IN`
  - `SENDS`
  - `CONTAINS`
  - `POSTED_IN`
  - `RELATED_TO`
- computes a simple offline baseline recommender
- writes Firestore-compatible cached recommendation payloads

## What it does not do yet

- no Firebase write-back yet
- no differential privacy module yet

## Run it

From the repository root:

```bash
python3 -m recsys.run_demo
```

This writes demo files under `recsys/output/demo/`:

- `synthetic_dataset.json`
- `hin_summary.json`
- `recommendations.json`
- `firestore_payloads.json`

## Run it on exported JSON

If you export Firestore collections to local JSON files, you can run the same offline pipeline on that snapshot:

```bash
python3 -m recsys.run_from_json \
  --users path/to/users.json \
  --rooms path/to/rooms.json \
  --messages path/to/messages.json \
  --output-dir /tmp/recsys-from-json
```

Expected input shape:

- `users.json`: list or object of user docs
- `rooms.json`: list or object of room docs
- `messages.json`: flat list or object of message docs with `roomId`

This stays fully offline and does not call Firebase.

## Prepare GraphSAGE artifacts

You can now prepare the exact local artifacts a future GraphSAGE trainer will need:

```bash
python3 -m recsys.run_graphsage_prep \
  --mode synthetic \
  --output-dir /tmp/recsys-graphsage-prep
```

Or from exported JSON:

```bash
python3 -m recsys.run_graphsage_prep \
  --mode json \
  --users path/to/users.json \
  --rooms path/to/rooms.json \
  --messages path/to/messages.json \
  --output-dir /tmp/recsys-graphsage-prep
```

This produces:

- feature names
- positive student-group pairs
- harder mixed negative samples
- pairwise training triplets
- warm/cold student split
- a prep summary for the future trainer

The prep layer is intentionally dependency-light. It works even when `torch` is not installed yet.

## Train the local GraphSAGE-style model

You can now train a lightweight GraphSAGE-style recommender fully offline with NumPy:

```bash
python3 -m recsys.run_graphsage_train \
  --mode synthetic \
  --hard-negative-ratio 0.67 \
  --output-dir /tmp/recsys-graphsage-train
```

This writes:

- `graphsage_prep_summary.json`
- `graphsage_training_summary.json`
- `graphsage_losses.json`
- `graphsage_embeddings.json`
- `graphsage_recommendations.json`
- `graphsage_firestore_payloads.json`

The trainer now uses:

- relation-weighted HIN aggregation
- pairwise ranking loss instead of simple pointwise classification
- a mix of hard negatives and random negatives
- a hybrid reranker that blends learned embedding similarity with profile and topic signals

## Train the local LightGCN baseline

You can now train a lightweight LightGCN baseline fully offline with NumPy:

```bash
python3 -m recsys.run_lightgcn_train \
  --mode synthetic \
  --hard-negative-ratio 0.67 \
  --output-dir /tmp/recsys-lightgcn-train
```

This writes:

- `lightgcn_prep_summary.json`
- `lightgcn_training_summary.json`
- `lightgcn_losses.json`
- `lightgcn_embeddings.json`
- `lightgcn_recommendations.json`
- `lightgcn_firestore_payloads.json`

The LightGCN slice intentionally stays close to the thesis baseline comparison:

- it uses only the transductive `Student`-`Group` membership graph
- it propagates embeddings over the bipartite graph without feature transforms
- it shares the same offline triplet setup as the GraphSAGE-style trainer for a cleaner model comparison

## Evaluate baseline vs GraphSAGE-style vs LightGCN

```bash
python3 -m recsys.run_evaluation \
  --mode synthetic \
  --output-dir /tmp/recsys-eval
```

The evaluator currently uses a simple leave-one-out setup and reports:

- `Precision@K`
- `Recall@K`
- `NDCG@K`
- `HitRate@K`
- `MRR`
- warm vs cold user splits
- `coldStartRobustness = NDCG@K(cold) / NDCG@K(warm)`
- delta vs the baseline for each user segment
- GraphSAGE vs LightGCN deltas for each user segment

## Bigger BA-style run

You can scale the synthetic setup closer to the thesis assumptions:

```bash
python3 -m recsys.run_demo --students 500 --groups 80 --topics 50 --messages-per-day 1200 --days 14 --top-k 10 --seed 42
```

## How this maps to the thesis

- `synthetic_data.py` covers the first BA2 need: local data generation without cost.
- `hin.py` turns platform entities into a typed HIN.
- `baselines.py` gives us a no-training baseline before GraphSAGE.
- `graphsage_prep.py` builds feature vectors, adjacency, and train/eval splits for the later GraphSAGE trainer.
- `graphsage_train.py` trains a lightweight local GraphSAGE-style encoder with relation-aware aggregation and pairwise ranking.
- `lightgcn_train.py` provides the thesis-style transductive baseline on top of `MEMBER_OF` edges only.
- `evaluation.py` compares baseline vs local GraphSAGE-style vs local LightGCN offline, including warm/cold deltas.
- `firestore_payloads.json` matches the app contract already implemented on Android.

## Baseline scoring

The current baseline is intentionally simple and explainable:

- `topic similarity`: student-topic to group-topic overlap
- `study path affinity`: how many current members share the student’s study path
- `semester proximity`: how close the student is to the group’s typical semester
- `serendipity`: `topic similarity * (1 - member similarity)`

That serendipity term is directly inspired by the ranking idea in BA1 Chapter 6.
