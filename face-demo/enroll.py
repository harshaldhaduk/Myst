"""Upload faces/<person_name>/*.jpg to the CompreFace recognition collection.

Usage:
    python enroll.py            # add photos for every person folder
    python enroll.py --replace  # delete each person's existing faces first (avoids duplicates on re-run)
"""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np
import requests

from common import FACES_DIR, CompreFaceClient, ConfigError, encode_jpeg, load_config

IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png"}
MAX_UPLOAD_SIDE = 1280  # CompreFace downsizes to 640 anyway; this keeps uploads under its 5MB limit


@dataclass(frozen=True)
class EnrollSummary:
    person: str
    uploaded: int
    no_face: tuple[str, ...]
    unreadable: tuple[str, ...]


def list_people(faces_dir: Path) -> list[Path]:
    return sorted(p for p in faces_dir.iterdir() if p.is_dir() and not p.name.startswith("."))


def list_images(person_dir: Path) -> list[Path]:
    return sorted(p for p in person_dir.iterdir() if p.suffix.lower() in IMAGE_SUFFIXES)


def shrink(image: np.ndarray, max_side: int = MAX_UPLOAD_SIDE) -> np.ndarray:
    scale = max_side / max(image.shape[:2])
    if scale >= 1:
        return image
    return cv2.resize(image, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)


def enroll_person(client: CompreFaceClient, person_dir: Path) -> EnrollSummary:
    name = person_dir.name
    uploaded = 0
    no_face: list[str] = []
    unreadable: list[str] = []
    for path in list_images(person_dir):
        image = cv2.imread(str(path))
        if image is None:
            unreadable.append(path.name)
            continue
        if client.add_face(name, encode_jpeg(shrink(image))):
            uploaded += 1
        else:
            no_face.append(path.name)
    return EnrollSummary(name, uploaded, tuple(no_face), tuple(unreadable))


def print_summary(summary: EnrollSummary) -> None:
    print(f"{summary.person}: {summary.uploaded} uploaded, "
          f"{len(summary.no_face)} skipped (no face), {len(summary.unreadable)} unreadable")
    for name in summary.no_face:
        print(f"    no face found: {name}")
    for name in summary.unreadable:
        print(f"    could not read: {name}")
    if summary.uploaded < 5:
        print("    warning: fewer than 5 usable photos; accuracy will be poor (aim for 10-20)")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--replace", action="store_true",
                        help="delete each person's existing faces in CompreFace before uploading")
    parser.add_argument("--faces-dir", type=Path, default=FACES_DIR)
    args = parser.parse_args()

    try:
        client = CompreFaceClient(load_config())
        if not args.faces_dir.is_dir():
            raise ConfigError(f"{args.faces_dir} does not exist")
        people = list_people(args.faces_dir)
        if not people:
            raise ConfigError(f"No person folders in {args.faces_dir}. Add faces/<name>/*.jpg first.")
        summaries = []
        for person_dir in people:
            if args.replace:
                client.delete_subject(person_dir.name)
            summary = enroll_person(client, person_dir)
            print_summary(summary)
            summaries.append(summary)
    except ConfigError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    except requests.RequestException as exc:
        print(f"error: CompreFace request failed: {exc}", file=sys.stderr)
        return 1

    total = sum(s.uploaded for s in summaries)
    print(f"\nDone: {total} photos enrolled across {len(summaries)} people.")
    return 0 if total else 1


if __name__ == "__main__":
    sys.exit(main())
