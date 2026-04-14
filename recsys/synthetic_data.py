from __future__ import annotations

import math
import random

from recsys.models import DatasetConfig
from recsys.models import Message
from recsys.models import Student
from recsys.models import StudyGroup
from recsys.models import SyntheticDataset
from recsys.models import semester_bucket_for
from recsys.models import utc_timestamp


FIRST_NAMES = [
    "Anna", "Ben", "Clara", "Daniel", "Elena", "Farid", "Greta", "Hassan",
    "Ines", "Jonas", "Klara", "Luca", "Mina", "Noah", "Olivia", "Paul",
    "Rosa", "Sara", "Tobias", "Yara",
]

LAST_NAMES = [
    "Amini", "Bauer", "Chen", "Davis", "Eder", "Fischer", "Gashi", "Huber",
    "Ivanov", "Jovanovic", "Khan", "Leitner", "Meyer", "Novak", "Ortiz",
    "Pichler", "Quinn", "Rahimi", "Schmidt", "Tomic",
]

STUDY_PATH_TOPICS = {
    "Computer Science and Digital Communications": [
        "kotlin", "firebase", "algorithms", "databases", "ai", "networks", "android"
    ],
    "Applied Electronics": [
        "circuits", "signals", "embedded", "iot", "sensors", "hardware", "measurement"
    ],
    "Biomedical Science": [
        "biostatistics", "lab", "genetics", "microbiology", "diagnostics", "ethics", "research"
    ],
    "Health Care and Nursing": [
        "care", "anatomy", "clinical", "documentation", "pharmacology", "emergency", "teamwork"
    ],
    "Social Work": [
        "counseling", "policy", "casework", "communication", "community", "ethics", "inclusion"
    ],
}


def _weighted_sample_without_replacement(
    rng: random.Random,
    items: list[str],
    weights: list[float],
    k: int,
) -> list[str]:
    chosen: list[str] = []
    pool = list(items)
    pool_weights = list(weights)

    for _ in range(min(k, len(pool))):
        total = sum(pool_weights)
        if total <= 0:
            break
        picked = rng.choices(pool, weights=pool_weights, k=1)[0]
        index = pool.index(picked)
        chosen.append(picked)
        pool.pop(index)
        pool_weights.pop(index)

    return chosen


def _make_group_description(study_path: str, topic_tags: list[str]) -> str:
    pretty_topics = ", ".join(topic_tags[:3])
    return (
        f"Study group for {study_path.lower()} students focusing on {pretty_topics}. "
        f"We review exercises, compare notes, and prepare together for exams."
    )


def _build_message_text(topic: str, group_name: str) -> str:
    return (
        f"Let's review {topic} for {group_name}. "
        f"I can share notes and explain the tricky parts before the next exam."
    )


def generate_synthetic_dataset(config: DatasetConfig, seed: int = 42) -> SyntheticDataset:
    rng = random.Random(seed)
    study_paths = list(STUDY_PATH_TOPICS.keys())

    groups: dict[str, StudyGroup] = {}
    shuffled_group_ids: list[str] = []

    for index in range(1, config.num_groups + 1):
        group_id = f"group-{index:03d}"
        primary_study_path = rng.choice(study_paths)
        primary_topics = STUDY_PATH_TOPICS[primary_study_path]
        topic_tags = rng.sample(primary_topics, k=min(3, len(primary_topics)))
        category = topic_tags[0].title()
        name = f"{category} Study Circle {index:02d}"
        groups[group_id] = StudyGroup(
            id=group_id,
            name=name,
            category=category,
            description=_make_group_description(primary_study_path, topic_tags),
            primary_study_path=primary_study_path,
            topic_tags=topic_tags,
        )
        shuffled_group_ids.append(group_id)

    rng.shuffle(shuffled_group_ids)
    popularity_bias = {
        group_id: 1.0 / math.sqrt(rank + 1)
        for rank, group_id in enumerate(shuffled_group_ids)
    }

    students: dict[str, Student] = {}

    for index in range(1, config.num_students + 1):
        study_path = rng.choice(study_paths)
        semester = rng.randint(1, 6)
        preferred_topics = rng.sample(STUDY_PATH_TOPICS[study_path], k=3)
        student_id = f"student-{index:03d}"
        first_name = rng.choice(FIRST_NAMES)
        last_name = rng.choice(LAST_NAMES)

        student = Student(
            id=student_id,
            first_name=first_name,
            last_name=last_name,
            study_path=study_path,
            semester=semester,
            semester_bucket=semester_bucket_for(semester),
            preferred_topics=preferred_topics,
        )

        target_groups = rng.randint(
            min(config.min_groups_per_student, config.num_groups),
            min(config.max_groups_per_student, config.num_groups),
        )
        candidate_ids = list(groups.keys())
        candidate_weights: list[float] = []

        for group_id in candidate_ids:
            group = groups[group_id]
            overlap = len(set(student.preferred_topics) & set(group.topic_tags))
            path_match = 1.8 if group.primary_study_path == study_path else 0.45
            weight = 0.2 + path_match + (0.9 * overlap) + popularity_bias[group_id]
            candidate_weights.append(weight)

        chosen_group_ids = _weighted_sample_without_replacement(
            rng=rng,
            items=candidate_ids,
            weights=candidate_weights,
            k=target_groups,
        )

        student.joined_group_ids.extend(chosen_group_ids)
        students[student_id] = student

        for group_id in chosen_group_ids:
            groups[group_id].member_ids.append(student_id)

    messages: list[Message] = []
    total_messages = config.messages_per_day * config.num_days
    available_groups = [group for group in groups.values() if group.member_ids]
    group_message_weights = [
        (len(group.member_ids) ** 1.35) + popularity_bias[group.id]
        for group in available_groups
    ]

    for index in range(1, total_messages + 1):
        group = rng.choices(available_groups, weights=group_message_weights, k=1)[0]
        sender_id = rng.choice(group.member_ids)
        student = students[sender_id]
        usable_topics = list(set(student.preferred_topics) & set(group.topic_tags)) or group.topic_tags
        topic = rng.choice(usable_topics)
        day = rng.randint(1, config.num_days)
        reaction_count = rng.choices([0, 1, 2, 3, 4], weights=[0.45, 0.25, 0.15, 0.1, 0.05], k=1)[0]
        messages.append(
            Message(
                id=f"message-{index:06d}",
                sender_id=sender_id,
                group_id=group.id,
                text=_build_message_text(topic, group.name),
                day=day,
                reaction_count=reaction_count,
            )
        )

    return SyntheticDataset(
        config=config,
        students=students,
        groups=groups,
        messages=messages,
        generated_at=utc_timestamp(),
        seed=seed,
    )

