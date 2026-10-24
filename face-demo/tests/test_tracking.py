from tracking import ABSENT_GRACE_FRAMES, Identity, TrackState, apply_result, iou, step

THRESHOLD = 0.85
INTERVAL = 0.4
BOX = (100, 100, 80, 80)
OTHER_BOX = (400, 300, 80, 80)
KNOWN = Identity("alice", 0.95, 120.0)
LOW = Identity("alice", 0.60, 120.0)


def advance(state, box, now):
    return step(state, box, now, THRESHOLD, INTERVAL)


def test_iou_identical_and_disjoint():
    assert iou(BOX, BOX) == 1.0
    assert iou(BOX, OTHER_BOX) == 0.0


def test_new_face_triggers_query_once_while_pending():
    state, query = advance(TrackState(), BOX, 0.0)
    assert query and state.pending
    _, again = advance(state, BOX, 1.0)
    assert not again


def test_known_identity_is_cached_and_not_requeried():
    state, _ = advance(TrackState(), BOX, 0.0)
    state = apply_result(state, state.generation, KNOWN)
    for t in (1.0, 2.0, 10.0):
        state, query = advance(state, BOX, t)
        assert not query
    assert state.identity == KNOWN


def test_unknown_result_is_retried_at_throttle_rate():
    state, _ = advance(TrackState(), BOX, 0.0)
    state = apply_result(state, state.generation, LOW)
    state, too_soon = advance(state, BOX, 0.2)
    state, due = advance(state, BOX, 0.5)
    assert not too_soon and due


def test_throttle_applies_across_a_new_face():
    state, _ = advance(TrackState(), BOX, 0.0)
    state = apply_result(state, state.generation, KNOWN)
    state, query = advance(state, OTHER_BOX, 0.1)
    assert not query  # new face, but still inside the throttle window
    _, query = advance(state, OTHER_BOX, 0.5)
    assert query


def test_different_face_clears_cached_identity():
    state, _ = advance(TrackState(), BOX, 0.0)
    state = apply_result(state, state.generation, KNOWN)
    state, _ = advance(state, OTHER_BOX, 5.0)
    assert state.identity is None


def test_face_leaving_resets_after_grace_period():
    state, _ = advance(TrackState(), BOX, 0.0)
    state = apply_result(state, state.generation, KNOWN)
    for i in range(ABSENT_GRACE_FRAMES):
        state, _ = advance(state, None, 1.0 + i)
    assert state.identity == KNOWN  # brief flicker keeps the cache
    state, _ = advance(state, None, 99.0)
    assert state.box is None and state.identity is None
    _, query = advance(state, BOX, 100.0)
    assert query


def test_stale_result_for_old_face_is_dropped():
    state, _ = advance(TrackState(), BOX, 0.0)
    old_generation = state.generation
    state, _ = advance(state, OTHER_BOX, 1.0)
    state = apply_result(state, old_generation, KNOWN)
    assert state.identity is None


def test_failed_query_frees_slot_without_losing_cache():
    state, _ = advance(TrackState(), BOX, 0.0)
    state = apply_result(state, state.generation, None)
    assert not state.pending
    _, query = advance(state, BOX, 1.0)
    assert query


def test_threshold_change_reevaluates_cached_identity():
    assert KNOWN.is_known(0.9) and not KNOWN.is_known(0.99)
    assert KNOWN.label(0.99) == "Unknown"
