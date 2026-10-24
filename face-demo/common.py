"""Shared config and CompreFace REST client for enroll.py and live_demo.py."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np
import requests
from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parent
FACES_DIR = ROOT / "faces"

DEFAULT_URL = "http://localhost:8000"
DEFAULT_THRESHOLD = 0.85
REQUEST_TIMEOUT_S = 30  # first request after server start is slow (model warm-up)
JPEG_QUALITY = 90
NO_FACE_CODE = 28  # CompreFace error code for "No face is found in the given image"
BAD_API_KEY_CODE = 10  # returned with HTTP 404: "Recognition service with API Key ... not found"


class ConfigError(RuntimeError):
    """Raised for problems the user must fix (bad key, server down, missing .env)."""


@dataclass(frozen=True)
class Config:
    url: str
    api_key: str
    threshold: float


@dataclass(frozen=True)
class Match:
    subject: str
    similarity: float


def load_config() -> Config:
    load_dotenv(ROOT / ".env")
    api_key = os.getenv("COMPREFACE_API_KEY", "").strip()
    if not api_key:
        raise ConfigError(
            "COMPREFACE_API_KEY is not set. Copy .env.example to .env and paste the "
            "Recognition service API key from the CompreFace UI."
        )
    raw_threshold = os.getenv("SIMILARITY_THRESHOLD", str(DEFAULT_THRESHOLD))
    try:
        threshold = float(raw_threshold)
    except ValueError as exc:
        raise ConfigError(f"SIMILARITY_THRESHOLD must be a number, got {raw_threshold!r}") from exc
    return Config(
        url=os.getenv("COMPREFACE_URL", DEFAULT_URL).rstrip("/"),
        api_key=api_key,
        threshold=threshold,
    )


def encode_jpeg(image: np.ndarray) -> bytes:
    ok, buf = cv2.imencode(".jpg", image, [cv2.IMWRITE_JPEG_QUALITY, JPEG_QUALITY])
    if not ok:
        raise ValueError("could not JPEG-encode image")
    return buf.tobytes()


def _error_code(response: requests.Response) -> int | None:
    try:
        return response.json().get("code")
    except ValueError:
        return None


def _raise_for_config_errors(response: requests.Response) -> None:
    if response.status_code in (401, 403) or _error_code(response) == BAD_API_KEY_CODE:
        raise ConfigError("CompreFace rejected the API key (check COMPREFACE_API_KEY in .env and that it is a Recognition service key).")


class CompreFaceClient:
    """Thin wrapper over the CompreFace Recognition REST API."""

    def __init__(self, config: Config, session: requests.Session | None = None):
        self._base = f"{config.url}/api/v1/recognition"
        self._session = session or requests.Session()
        self._session.headers["x-api-key"] = config.api_key

    def _post_image(self, path: str, jpeg: bytes, params: dict) -> requests.Response:
        try:
            return self._session.post(
                f"{self._base}{path}",
                params=params,
                files={"file": ("face.jpg", jpeg, "image/jpeg")},
                timeout=REQUEST_TIMEOUT_S,
            )
        except requests.ConnectionError as exc:
            raise ConfigError(
                "Cannot reach CompreFace. Is the server running? "
                "(cd compreface && docker compose up -d)"
            ) from exc

    def add_face(self, subject: str, jpeg: bytes) -> bool:
        """Upload an example face. Returns False if the image contains no face."""
        response = self._post_image("/faces", jpeg, {"subject": subject})
        _raise_for_config_errors(response)
        if response.status_code == 400 and _error_code(response) == NO_FACE_CODE:
            return False
        response.raise_for_status()
        return True

    def recognize(self, jpeg: bytes) -> Match | None:
        """Best match for the largest face in the image, or None if no face found."""
        response = self._post_image("/recognize", jpeg, {"limit": 1, "prediction_count": 1})
        _raise_for_config_errors(response)
        if response.status_code == 400 and _error_code(response) == NO_FACE_CODE:
            return None
        response.raise_for_status()
        results = response.json().get("result", [])
        subjects = results[0].get("subjects", []) if results else []
        if not subjects:
            return None
        top = subjects[0]
        return Match(subject=top["subject"], similarity=float(top["similarity"]))

    def delete_subject(self, subject: str) -> None:
        """Remove a subject and all of their example faces (used by enroll --replace)."""
        try:
            response = self._session.delete(f"{self._base}/subjects/{subject}", timeout=REQUEST_TIMEOUT_S)
        except requests.ConnectionError as exc:
            raise ConfigError("Cannot reach CompreFace. Is the server running?") from exc
        _raise_for_config_errors(response)
        if response.status_code not in (200, 404):
            response.raise_for_status()
