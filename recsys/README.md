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

- no full GraphSAGE training loop yet
- no LightGCN baseline yet
- no Firebase export yet
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
- negative samples
- warm/cold student split
- a prep summary for the future trainer

The prep layer is intentionally dependency-light. It works even when `torch` is not installed yet.

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
- `firestore_payloads.json` matches the app contract already implemented on Android.

## Baseline scoring

The current baseline is intentionally simple and explainable:

- `topic similarity`: student-topic to group-topic overlap
- `study path affinity`: how many current members share the student’s study path
- `semester proximity`: how close the student is to the group’s typical semester
- `serendipity`: `topic similarity * (1 - member similarity)`

That serendipity term is directly inspired by the ranking idea in BA1 Chapter 6.
