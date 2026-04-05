from __future__ import annotations

import math
import re
from collections import Counter
from collections import defaultdict

from recsys.models import HINEdge
from recsys.models import HINGraph
from recsys.models import HINNode
from recsys.models import SyntheticDataset


TOKEN_RE = re.compile(r"[a-z0-9]+")
STOPWORDS = {
    "and", "for", "the", "with", "that", "this", "from", "into", "before", "next",
    "can", "share", "lets", "review", "group", "study", "students", "focusing", "notes",
    "compare", "prepare", "together", "exam", "exams", "parts", "tricky",
}


def tokenize(text: str) -> list[str]:
    return [
        token
        for token in TOKEN_RE.findall(text.lower().replace("_", " "))
        if len(token) > 2 and token not in STOPWORDS
    ]


def _compute_tfidf_vectors(
    documents: dict[str, list[str]],
    max_topics: int,
) -> tuple[list[str], dict[str, dict[str, float]]]:
    if not documents:
        return [], {}

    document_frequency: Counter[str] = Counter()
    for tokens in documents.values():
        document_frequency.update(set(tokens))

    num_documents = len(documents)
    inverse_document_frequency = {
        token: math.log((1 + num_documents) / (1 + frequency)) + 1.0
        for token, frequency in document_frequency.items()
    }

    corpus_score: Counter[str] = Counter()
    raw_vectors: dict[str, dict[str, float]] = {}

    for document_id, tokens in documents.items():
        term_frequency = Counter(tokens)
        token_count = max(len(tokens), 1)
        vector: dict[str, float] = {}
        for token, count in term_frequency.items():
            weight = (count / token_count) * inverse_document_frequency[token]
            vector[token] = weight
            corpus_score[token] += weight
        raw_vectors[document_id] = vector

    selected_topics = [token for token, _ in corpus_score.most_common(max_topics)]
    filtered_vectors: dict[str, dict[str, float]] = {}
    for document_id, vector in raw_vectors.items():
        filtered_vectors[document_id] = {
            token: weight
            for token, weight in vector.items()
            if token in selected_topics
        }

    return selected_topics, filtered_vectors


def build_hin(dataset: SyntheticDataset, max_topics: int = 50) -> HINGraph:
    nodes: dict[str, HINNode] = {}
    edges: list[HINEdge] = []

    messages_by_group: dict[str, list[str]] = defaultdict(list)
    messages_by_student: dict[str, list[str]] = defaultdict(list)

    for student in dataset.students.values():
        nodes[student.id] = HINNode(
            id=student.id,
            node_type="Student",
            features={
                "studyPath": student.study_path,
                "semester": student.semester,
                "semesterBucket": student.semester_bucket,
            },
        )

    for group in dataset.groups.values():
        nodes[group.id] = HINNode(
            id=group.id,
            node_type="Group",
            features={
                "category": group.category,
                "description": group.description,
                "primaryStudyPath": group.primary_study_path,
            },
        )

    for student in dataset.students.values():
        for group_id in student.joined_group_ids:
            edges.append(HINEdge(source=student.id, target=group_id, relation="MEMBER_OF"))

    for message in dataset.messages:
        nodes[message.id] = HINNode(
            id=message.id,
            node_type="Message",
            features={
                "day": message.day,
                "length": len(message.text.split()),
                "reactionCount": message.reaction_count,
            },
        )
        edges.append(HINEdge(source=message.sender_id, target=message.id, relation="SENDS"))
        edges.append(HINEdge(source=message.id, target=message.group_id, relation="POSTED_IN"))
        edges.append(HINEdge(source=message.group_id, target=message.id, relation="CONTAINS"))
        messages_by_group[message.group_id].append(message.text)
        messages_by_student[message.sender_id].append(message.text)

    group_documents: dict[str, list[str]] = {}
    student_documents: dict[str, list[str]] = {}

    for group in dataset.groups.values():
        text = " ".join([group.description, *group.topic_tags, *messages_by_group[group.id]])
        group_documents[group.id] = tokenize(text)

    for student in dataset.students.values():
        text = " ".join(messages_by_student.get(student.id, []))
        student_documents[student.id] = tokenize(text)

    selected_topics, group_topic_weights = _compute_tfidf_vectors(group_documents, max_topics=max_topics)
    _, student_topic_weights = _compute_tfidf_vectors(student_documents, max_topics=max_topics)

    for topic in selected_topics:
        topic_id = f"topic:{topic}"
        nodes[topic_id] = HINNode(id=topic_id, node_type="Topic", features={"label": topic})

    for group_id, vector in group_topic_weights.items():
        for topic, weight in vector.items():
            if weight > 0:
                edges.append(
                    HINEdge(
                        source=group_id,
                        target=f"topic:{topic}",
                        relation="RELATED_TO",
                        weight=weight,
                    )
                )

    for student_id, vector in student_topic_weights.items():
        for topic, weight in vector.items():
            if weight > 0:
                edges.append(
                    HINEdge(
                        source=student_id,
                        target=f"topic:{topic}",
                        relation="INTERESTED_IN",
                        weight=weight,
                    )
                )

    return HINGraph(
        nodes=nodes,
        edges=edges,
        group_topic_weights=group_topic_weights,
        student_topic_weights=student_topic_weights,
        selected_topics=selected_topics,
    )

