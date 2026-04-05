# BA2 Implementation Roadmap

This document maps the bachelor thesis architecture to the current `FhChatRoom` codebase so implementation stays aligned with the research.

## What BA1/BA2 require

- Android app collects cold-start features for each student: `studyPath` and `semester`.
- Group documents expose content features such as `category` and `description`.
- Firebase remains the system of record for real-time app data.
- A separate recommender pipeline exports Firestore data, builds the HIN, trains the graph model, and writes cached recommendation IDs back to user documents.
- The app reads cached recommendation IDs and falls back gracefully when no batch result exists yet.

## What is implemented in this step

- `User` now stores:
  - `studyPath`
  - `semester`
  - `semesterBucket`
  - `recommendedRoomIds`
  - `recommendationsUpdatedAt`
  - `recommendationSource`
- `Room` now stores:
  - `category`
- Sign-up and profile editing now capture the academic profile required for cold-start recommendation.
- Room creation now captures an optional category for future HIN node features.
- The room list screen now shows a `Recommended for you` section.
  - If cached recommendation IDs exist, the UI uses them.
  - If not, the app shows a popularity-based fallback, matching the BA1 batch-architecture assumption.

## Firestore contract for the future recommender

The future Python graph engine should be able to write the following fields into each user document:

```json
{
  "recommendedRoomIds": ["roomA", "roomB", "roomC"],
  "recommendationsUpdatedAt": 1712236800000,
  "recommendationSource": "GRAPH_SAGE"
}
```

The app already understands this contract.

## Suggested next implementation phases

1. Add a Python `recsys` module for Firestore export and synthetic dataset generation.
2. Build the HIN transformer:
   - Student nodes
   - Group nodes
   - Message nodes
   - Topic nodes
   - typed edges / meta-path support
3. Implement a simple offline baseline first:
   - popularity
   - content-based
4. Add GraphSAGE training and cached write-back.
5. Add LightGCN as the BA2 baseline.
6. Add evaluation scripts for:
   - NDCG@10
   - Precision@10
   - Recall@10
   - cold-start split
   - privacy / fairness experiments

## Why this order

This sequence keeps the project recordable and teachable:

- first define the data contract,
- then export and model the data,
- then add simple baselines,
- then add the full graph models,
- and only after that run BA2 evaluation.

## Current local prototype

The repository now also contains a local `recsys/` package that:

- generates a synthetic FH-style dataset offline,
- builds a typed HIN with `Student`, `Group`, `Message`, and `Topic` nodes,
- computes a simple no-training baseline recommender,
- writes Firestore-compatible cached recommendation payloads.

This is the zero-cost bridge between the Android app scaffolding and the later GraphSAGE implementation.
