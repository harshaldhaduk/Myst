import numpy as np

from live_demo import crop_face, largest_face_box


class _Rel:
    def __init__(self, xmin, ymin, width, height):
        self.xmin, self.ymin, self.width, self.height = xmin, ymin, width, height


class _Det:
    def __init__(self, *rel):
        self.location_data = type("L", (), {"relative_bounding_box": _Rel(*rel)})()


def test_largest_face_wins_and_none_when_empty():
    dets = [_Det(0.1, 0.1, 0.1, 0.1), _Det(0.5, 0.5, 0.3, 0.3)]
    assert largest_face_box(dets, 100, 100) == (50, 50, 30, 30)
    assert largest_face_box(None, 100, 100) is None


def test_box_is_clamped_to_frame():
    assert largest_face_box([_Det(-0.1, 0.9, 0.5, 0.5)], 100, 100) == (0, 90, 50, 10)


def test_crop_is_padded_resized_and_clamped():
    frame = np.zeros((480, 640, 3), dtype=np.uint8)
    crop = crop_face(frame, (300, 200, 100, 100), side=160)
    assert max(crop.shape[:2]) == 160
    edge = crop_face(frame, (0, 0, 100, 100), side=160)  # padding must not go negative
    assert max(edge.shape[:2]) == 160
