"""Webcam demo of the glasses recognition pipeline.

Local MediaPipe detector on every frame -> padded small face crop -> CompreFace
recognize (throttled, only for new faces) -> name/similarity/latency overlay.

Keys: q or Esc quit, = / - raise / lower the similarity threshold by 0.01.
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import time
from concurrent.futures import Future, ThreadPoolExecutor

import cv2
import mediapipe as mp
import numpy as np
import requests

from common import CompreFaceClient, ConfigError, encode_jpeg, load_config
from tracking import Box, Identity, TrackState, apply_result, step

DEFAULT_MAX_RPS = 2.5
DEFAULT_CROP_SIDE = 160
CROP_PADDING = 0.3  # fraction of the box size added on each side
DETECTION_CONFIDENCE = 0.6
SPEAK_COOLDOWN_S = 30  # don't re-announce the same person within this window
THRESHOLD_STEP = 0.01

GREEN, ORANGE, GREY = (80, 200, 80), (0, 140, 255), (180, 180, 180)


def largest_face_box(detections, frame_w: int, frame_h: int) -> Box | None:
    boxes = []
    for det in detections or []:
        rel = det.location_data.relative_bounding_box
        x, y = max(0, int(rel.xmin * frame_w)), max(0, int(rel.ymin * frame_h))
        w, h = min(int(rel.width * frame_w), frame_w - x), min(int(rel.height * frame_h), frame_h - y)
        if w > 0 and h > 0:
            boxes.append((x, y, w, h))
    return max(boxes, key=lambda b: b[2] * b[3], default=None)


def crop_face(frame: np.ndarray, box: Box, side: int = DEFAULT_CROP_SIDE) -> np.ndarray:
    x, y, w, h = box
    pad_x, pad_y = int(w * CROP_PADDING), int(h * CROP_PADDING)
    x0, y0 = max(0, x - pad_x), max(0, y - pad_y)
    x1, y1 = min(frame.shape[1], x + w + pad_x), min(frame.shape[0], y + h + pad_y)
    crop = frame[y0:y1, x0:x1]
    scale = side / max(crop.shape[:2])
    return cv2.resize(crop, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)


def query_identity(client: CompreFaceClient, crop: np.ndarray) -> Identity:
    jpeg = encode_jpeg(crop)
    started = time.perf_counter()
    match = client.recognize(jpeg)
    latency_ms = (time.perf_counter() - started) * 1000
    if match is None:
        return Identity(subject=None, similarity=0.0, latency_ms=latency_ms)
    return Identity(match.subject, match.similarity, latency_ms)


class Speaker:
    """Local TTS: macOS `say`, else pyttsx3. Announces a name once per new person."""

    def __init__(self) -> None:
        self._last_name: str | None = None
        self._last_at = float("-inf")
        self._say = shutil.which("say")
        self._engine = None
        if not self._say:
            import pyttsx3  # imported lazily; only needed off macOS

            self._engine = pyttsx3.init()

    def announce(self, name: str, now: float) -> None:
        if name == self._last_name and now - self._last_at < SPEAK_COOLDOWN_S:
            return
        self._last_name, self._last_at = name, now
        text = name.replace("_", " ")
        if self._say:
            subprocess.Popen([self._say, text])  # noqa: S603 - fixed binary, no shell
        elif self._engine:
            self._engine.say(text)
            self._engine.runAndWait()


def draw_overlay(frame: np.ndarray, box: Box, identity: Identity | None, pending: bool, threshold: float) -> None:
    x, y, w, h = box
    if identity is None:
        color, text = GREY, "Searching..." if pending else "Detected"
    else:
        color = GREEN if identity.is_known(threshold) else ORANGE
        score = f" {identity.similarity:.2f}" if identity.subject else ""
        text = f"{identity.label(threshold)}{score}  {identity.latency_ms:.0f} ms"
    cv2.rectangle(frame, (x, y), (x + w, y + h), color, 2)
    cv2.putText(frame, text, (x, max(20, y - 8)), cv2.FONT_HERSHEY_SIMPLEX, 0.7, color, 2, cv2.LINE_AA)


def draw_hud(frame: np.ndarray, threshold: float, error: str | None) -> None:
    cv2.putText(frame, f"threshold {threshold:.2f}  (-/= to tune, q to quit)", (10, 24),
                cv2.FONT_HERSHEY_SIMPLEX, 0.6, GREY, 1, cv2.LINE_AA)
    if error:
        cv2.putText(frame, error, (10, frame.shape[0] - 12), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2, cv2.LINE_AA)


def run(args: argparse.Namespace) -> int:
    config = load_config()
    threshold = args.threshold if args.threshold is not None else config.threshold
    client = CompreFaceClient(config)
    speaker = Speaker() if args.speak else None

    cap = cv2.VideoCapture(args.camera)
    if not cap.isOpened():
        raise ConfigError(
            f"Cannot open camera {args.camera}. On macOS, allow camera access for your "
            "terminal in System Settings > Privacy & Security > Camera."
        )

    detector = mp.solutions.face_detection.FaceDetection(
        model_selection=0, min_detection_confidence=DETECTION_CONFIDENCE
    )
    min_interval = 1.0 / args.max_rps
    state = TrackState()
    inflight: tuple[Future, int] | None = None
    error: str | None = None

    with ThreadPoolExecutor(max_workers=1) as pool:
        try:
            while True:
                ok, frame = cap.read()
                if not ok:
                    raise ConfigError("Camera stopped returning frames.")
                frame = cv2.flip(frame, 1)  # mirror so it behaves like a mirror

                result = detector.process(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
                box = largest_face_box(result.detections, frame.shape[1], frame.shape[0])
                now = time.monotonic()
                state, should_query = step(state, box, now, threshold, min_interval)

                if inflight and inflight[0].done():
                    future, generation = inflight
                    inflight = None
                    try:
                        identity = future.result()
                        error = None
                    except ConfigError:
                        raise
                    except requests.RequestException as exc:
                        identity, error = None, f"request failed: {exc.__class__.__name__}"
                    was_known = state.identity is not None and state.identity.is_known(threshold)
                    state = apply_result(state, generation, identity)
                    if speaker and identity and identity.is_known(threshold) and not was_known:
                        speaker.announce(identity.subject, now)

                if should_query and box is not None:
                    crop = crop_face(frame, box, args.crop_size)
                    inflight = (pool.submit(query_identity, client, crop), state.generation)

                if box is not None:
                    draw_overlay(frame, box, state.identity, state.pending, threshold)
                draw_hud(frame, threshold, error)
                cv2.imshow("Myst face demo", frame)

                key = cv2.waitKey(1) & 0xFF
                if key in (ord("q"), 27):
                    return 0
                if key == ord("="):
                    threshold = min(1.0, round(threshold + THRESHOLD_STEP, 2))
                elif key == ord("-"):
                    threshold = max(0.0, round(threshold - THRESHOLD_STEP, 2))
        finally:
            cap.release()
            detector.close()
            cv2.destroyAllWindows()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--speak", action="store_true", help="speak each newly recognized name")
    parser.add_argument("--threshold", type=float, help="similarity cutoff (overrides SIMILARITY_THRESHOLD in .env)")
    parser.add_argument("--max-rps", type=float, default=DEFAULT_MAX_RPS, help="max recognize requests per second")
    parser.add_argument("--crop-size", type=int, default=DEFAULT_CROP_SIDE, help="longest side of the face crop sent to CompreFace")
    parser.add_argument("--camera", type=int, default=0, help="webcam index")
    args = parser.parse_args()
    try:
        return run(args)
    except ConfigError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 0


if __name__ == "__main__":
    sys.exit(main())
