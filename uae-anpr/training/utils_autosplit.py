"""Utility to automatically split raw images into train/val folders.

When the training and validation folders are empty, this script searches for
all image files directly under ``data/images`` (excluding the ``train`` and
``val`` sub-directories) and moves them into train/val with an 85/15 split.

The script is intentionally simple so it can run on both Windows and Linux.
"""
from __future__ import annotations

import argparse
import random
import shutil
from pathlib import Path
from typing import Iterable, List

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff"}


def list_images(paths: Iterable[Path]) -> List[Path]:
    """Return all files with image extensions inside ``paths`` (non recursive)."""
    files: List[Path] = []
    for path in paths:
        if not path.exists() or not path.is_dir():
            continue
        for candidate in path.iterdir():
            if candidate.is_file() and candidate.suffix.lower() in IMAGE_EXTENSIONS:
                files.append(candidate)
    return files


def main() -> None:
    parser = argparse.ArgumentParser(description="Auto-split images into train/val")
    parser.add_argument("--seed", type=int, default=42, help="Random seed for shuffling")
    args = parser.parse_args()

    root = Path(__file__).resolve().parent
    images_dir = root / "data" / "images"
    train_dir = images_dir / "train"
    val_dir = images_dir / "val"

    train_dir.mkdir(parents=True, exist_ok=True)
    val_dir.mkdir(parents=True, exist_ok=True)

    if any(train_dir.iterdir()) or any(val_dir.iterdir()):
        print("Train/val directories are not empty. Skipping auto split.")
        return

    candidate_dirs = [images_dir]
    raw_images = [
        img for img in list_images(candidate_dirs)
        if img.parent.name not in {"train", "val"}
    ]

    if not raw_images:
        print("No raw images found under data/images. Nothing to split.")
        return

    random.Random(args.seed).shuffle(raw_images)
    split_index = max(1, int(len(raw_images) * 0.85)) if len(raw_images) > 1 else len(raw_images)

    train_images = raw_images[:split_index]
    val_images = raw_images[split_index:]

    def move_images(images: List[Path], destination: Path) -> None:
        destination.mkdir(parents=True, exist_ok=True)
        for image_path in images:
            target = destination / image_path.name
            print(f"Moving {image_path} -> {target}")
            shutil.move(str(image_path), str(target))

    move_images(train_images, train_dir)
    move_images(val_images, val_dir)

    print(f"Split {len(raw_images)} images -> train: {len(train_images)}, val: {len(val_images)}")


if __name__ == "__main__":
    main()
