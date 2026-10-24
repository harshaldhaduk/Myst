"""Pure per-face state machine: decides when to query CompreFace and caches the result.

Mirrors the planned phone pipeline: a cheap local detector runs every frame, the
expensive recognition call only happens for a new face, throttled, and a confident
identification is cached until the face leaves or is replaced.
"""

from __future__ import annotations

from dataclasses import dataclass, replace

Box = tuple[int, int, int, int]  # x, y, w, h in pixels

ABSENT_GRACE_FRAMES = 15  # tolerate detector flicker before declaring the face gone
NEW_FACE_IOU = 0.2  # box overlap below this between frames means a different face


@dataclass(frozen=True)
class Identity:
    subject: str | None  # None when CompreFace found no face in the crop
    similarity: float
    latency_ms: float

    def is_known(self, threshold: float) -> bool:
        return self.subject is not None and self.similarity >= threshold

    def label(self, threshold: float) -> str:
        return self.subject if self.is_known(threshold) and self.subject else "Unknown"


@dataclass(frozen=True)
class TrackState:
    box: Box | None = None
    absent_frames: int = 0
    identity: Identity | None = None
    pending: bool = False
    last_query_at: float = float("-inf")
    generation: int = 0  # bumps whenever the tracked face changes; stale results are dropped


def iou(a: Box, b: Box) -> float:
    ax2, ay2, bx2, by2 = a[0] + a[2], a[1] + a[3], b[0] + b[2], b[1] + b[3]
    inter_w = max(0, min(ax2, bx2) - max(a[0], b[0]))
    inter_h = max(0, min(ay2, by2) - max(a[1], b[1]))
    inter = inter_w * inter_h
    union = a[2] * a[3] + b[2] * b[3] - inter
    return inter / union if union else 0.0


def step(
    state: TrackState,
    box: Box | None,
    now: float,
    threshold: float,
    min_interval_s: float,
) -> tuple[TrackState, bool]:
    """Advance one frame. Returns (new_state, should_query)."""
    if box is None:
        return _handle_absent(state), False

    is_new_face = state.box is None or iou(state.box, box) < NEW_FACE_IOU
    if is_new_face:
        state = TrackState(box=box, generation=state.generation + 1, last_query_at=state.last_query_at)
    else:
        state = replace(state, box=box, absent_frames=0)

    already_identified = state.identity is not None and state.identity.is_known(threshold)
    due = now - state.last_query_at >= min_interval_s
    if already_identified or state.pending or not due:
        return state, False
    return replace(state, pending=True, last_query_at=now), True


def _handle_absent(state: TrackState) -> TrackState:
    if state.box is None:
        return state
    absent = state.absent_frames + 1
    if absent > ABSENT_GRACE_FRAMES:
        return TrackState(generation=state.generation + 1, last_query_at=state.last_query_at)
    return replace(state, absent_frames=absent)


def apply_result(state: TrackState, generation: int, identity: Identity | None) -> TrackState:
    """Record a finished query. A failed query (identity=None) just frees the slot to retry."""
    if generation != state.generation:
        return state
    return replace(state, pending=False, identity=identity if identity is not None else state.identity)
