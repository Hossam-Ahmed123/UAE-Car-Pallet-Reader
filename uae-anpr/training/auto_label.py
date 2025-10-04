"""Auto-generate YOLO labels using a pre-trained licence plate detector."""
from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List, Sequence, Tuple

import numpy as np
try:
    import cv2
except ImportError:  # pragma: no cover - OpenCV should be available via requirements
    cv2 = None

from ultralytics import YOLO


def ensure_huggingface_available(model_name: str) -> None:
    """Raise a helpful error when a Hugging Face model cannot be resolved."""

    if "/" not in model_name:
        return

    candidate = Path(model_name)
    if candidate.exists():
        return

    try:
        import huggingface_hub  # type: ignore # noqa: F401
    except ImportError as exc:  # pragma: no cover - only triggered on misconfiguration
        raise RuntimeError(
            "The requested model appears to be hosted on the Hugging Face Hub but the "
            "'huggingface-hub' package is not installed. Install it with "
            "`pip install huggingface-hub` or add it to your environment before "
            "running auto_label.py."
        ) from exc


SUPPORTED_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff", ".webp"}


@dataclass(frozen=True)
class ImageLabelPair:
    image_path: Path
    label_path: Path


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Run a pre-trained licence plate detector over an image directory and "
            "write YOLO-format annotations into the matching labels directory."
        )
    )
    default_root = Path(__file__).resolve().parent / "data"
    parser.add_argument(
        "--images",
        type=Path,
        default=default_root / "images",
        help="Root directory that contains plate images (train/ and val/ splits supported).",
    )
    parser.add_argument(
        "--labels",
        type=Path,
        default=default_root / "labels",
        help="Destination directory for YOLO labels (mirrors the images directory structure).",
    )
    parser.add_argument(
        "--model",
        type=str,
        default="keremberke/yolov8n-license-plate",
        help="Ultralytics model checkpoint to use for auto-labelling.",
    )
    parser.add_argument(
        "--conf",
        type=float,
        default=0.25,
        help="Confidence threshold for detections (matches Ultralytics default).",
    )
    parser.add_argument(
        "--iou",
        type=float,
        default=0.7,
        help="IoU threshold for Non-Maximum Suppression (Ultralytics default is 0.7).",
    )
    parser.add_argument(
        "--device",
        type=str,
        default=None,
        help="Optional device override passed to Ultralytics (e.g. 'cpu', '0').",
    )
    parser.add_argument(
        "--batch",
        type=int,
        default=16,
        help="Batch size to use when running inference.",
    )
    parser.add_argument(
        "--class-id",
        type=int,
        default=None,
        help=(
            "Override the class id written to each label entry. If omitted the model's "
            "predicted class ids are preserved."
        ),
    )
    parser.add_argument(
        "--skip-existing",
        action="store_true",
        help="Skip images that already have a corresponding label file.",
    )
    parser.add_argument(
        "--enhance",
        nargs="*",
        choices=("clahe", "gamma", "sharpen"),
        default=(),
        help=(
            "Apply simple image enhancements before detection. "
            "Supported options: clahe (contrast-limited adaptive histogram equalisation), "
            "gamma (power-law correction), sharpen (unsharp masking)."
        ),
    )
    parser.add_argument(
        "--gamma-value",
        type=float,
        default=1.5,
        help=(
            "Gamma value used when --enhance includes 'gamma'. "
            "Values > 1.0 brighten the image, < 1.0 darken it."
        ),
    )
    parser.add_argument(
        "--sharpen-strength",
        type=float,
        default=0.5,
        help=(
            "Sharpening intensity for unsharp masking when --enhance includes 'sharpen'."
        ),
    )
    parser.add_argument(
        "--recursive",
        action="store_true",
        help="Recursively search the images directory (enabled by default when sub-folders exist).",
    )
    parser.add_argument(
        "--extensions",
        nargs="*",
        default=sorted(SUPPORTED_SUFFIXES),
        help="Image file extensions to include (case insensitive).",
    )
    return parser.parse_args(argv)


def normalise_extensions(extensions: Iterable[str]) -> List[str]:
    return [ext if ext.startswith(".") else f".{ext}" for ext in extensions]


def collect_images(images_root: Path, recursive: bool, extensions: Sequence[str]) -> List[Path]:
    if not images_root.exists():
        raise FileNotFoundError(f"Images directory does not exist: {images_root}")
    if not images_root.is_dir():
        raise NotADirectoryError(f"Images path is not a directory: {images_root}")

    suffixes = {ext.lower() for ext in extensions}
    pattern = "**/*" if recursive else "*"

    image_paths: List[Path] = []
    for path in images_root.glob(pattern):
        if path.is_file() and path.suffix.lower() in suffixes:
            image_paths.append(path)

    return sorted(image_paths)


def pair_with_labels(
    images_root: Path,
    labels_root: Path,
    image_paths: Sequence[Path],
    skip_existing: bool,
) -> Tuple[List[ImageLabelPair], int]:
    labels_root.mkdir(parents=True, exist_ok=True)

    pairs: List[ImageLabelPair] = []
    skipped = 0
    for image_path in image_paths:
        try:
            relative_path = image_path.relative_to(images_root)
        except ValueError:
            # Fallback: do not preserve structure if the image is outside the root
            relative_path = image_path.name

        label_path = labels_root / Path(relative_path).with_suffix(".txt")
        label_path.parent.mkdir(parents=True, exist_ok=True)

        if skip_existing and label_path.exists():
            skipped += 1
            continue

        pairs.append(ImageLabelPair(image_path=image_path, label_path=label_path))

    return pairs, skipped


def ensure_opencv_available() -> None:
    if cv2 is None:
        raise RuntimeError(
            "OpenCV is required for image enhancement but is not installed. "
            "Install the training requirements (`pip install -r training/requirements.txt`)."
        )


def apply_enhancements(
    image: np.ndarray,
    enhancements: Sequence[str],
    gamma_value: float,
    sharpen_strength: float,
) -> np.ndarray:
    enhanced = image

    for enhancement in enhancements:
        if enhancement == "clahe":
            lab = cv2.cvtColor(enhanced, cv2.COLOR_BGR2LAB)
            l_channel, a_channel, b_channel = cv2.split(lab)
            clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8, 8))
            l_channel = clahe.apply(l_channel)
            lab = cv2.merge((l_channel, a_channel, b_channel))
            enhanced = cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)
        elif enhancement == "gamma":
            gamma = max(gamma_value, 1e-6)
            inv_gamma = 1.0 / gamma
            table = np.array([
                ((i / 255.0) ** inv_gamma) * 255 for i in np.arange(256)
            ]).astype("uint8")
            enhanced = cv2.LUT(enhanced, table)
        elif enhancement == "sharpen":
            blurred = cv2.GaussianBlur(enhanced, (0, 0), sigmaX=3)
            enhanced = cv2.addWeighted(enhanced, 1 + sharpen_strength, blurred, -sharpen_strength, 0)

    return enhanced


def load_image_with_enhancements(
    image_path: Path,
    enhancements: Sequence[str],
    gamma_value: float,
    sharpen_strength: float,
) -> np.ndarray:
    ensure_opencv_available()

    image = cv2.imread(str(image_path))
    if image is None:
        raise FileNotFoundError(f"Failed to read image: {image_path}")

    if enhancements:
        image = apply_enhancements(
            image=image,
            enhancements=enhancements,
            gamma_value=gamma_value,
            sharpen_strength=sharpen_strength,
        )

    return image


def run_detector(
    model_name: str,
    pairs: Sequence[ImageLabelPair],
    conf: float,
    iou: float,
    device: str | None,
    batch: int,
    enhancements: Sequence[str],
    gamma_value: float,
    sharpen_strength: float,
) -> List[Tuple[ImageLabelPair, List[Tuple[int, float, float, float, float]]]]:
    ensure_huggingface_available(model_name)
    model = YOLO(model_name)

    if enhancements:
        sources = [
            load_image_with_enhancements(
                pair.image_path,
                enhancements=enhancements,
                gamma_value=gamma_value,
                sharpen_strength=sharpen_strength,
            )
            for pair in pairs
        ]
    else:
        sources = [str(pair.image_path) for pair in pairs]

    results = model.predict(
        source=sources,
        conf=conf,
        iou=iou,
        device=device,
        batch=batch,
        verbose=False,
    )

    paired_results: List[Tuple[ImageLabelPair, List[Tuple[int, float, float, float, float]]]] = []
    for pair, result in zip(pairs, results):
        boxes = getattr(result, "boxes", None)
        predictions: List[Tuple[int, float, float, float, float]] = []
        if boxes is not None and len(boxes) > 0:
            xywhn = boxes.xywhn.tolist()
            cls_values = boxes.cls.tolist() if boxes.cls is not None else [0] * len(xywhn)
            for cls_value, (x_center, y_center, width, height) in zip(cls_values, xywhn):
                predictions.append(
                    (
                        int(cls_value),
                        float(x_center),
                        float(y_center),
                        float(width),
                        float(height),
                    )
                )
        paired_results.append((pair, predictions))

    return paired_results


def write_labels(
    paired_results: Sequence[Tuple[ImageLabelPair, List[Tuple[int, float, float, float, float]]]],
    class_id: int | None,
) -> Tuple[int, int]:
    written = 0
    empty = 0

    for pair, predictions in paired_results:
        lines = []
        if predictions:
            for predicted_class, x_center, y_center, width, height in predictions:
                label_class = class_id if class_id is not None else predicted_class
                lines.append(
                    f"{label_class} {x_center:.6f} {y_center:.6f} {width:.6f} {height:.6f}"
                )
        else:
            empty += 1

        pair.label_path.write_text("\n".join(lines), encoding="utf-8")
        written += 1

    return written, empty


def main(argv: Sequence[str] | None = None) -> None:
    args = parse_args(argv)

    images_root = args.images.resolve()
    labels_root = args.labels.resolve()

    extensions = normalise_extensions(args.extensions)
    recursive = args.recursive or any(path.is_dir() for path in images_root.iterdir())

    image_paths = collect_images(images_root, recursive=recursive, extensions=extensions)
    if not image_paths:
        print(f"No images found under {images_root} with extensions: {', '.join(extensions)}")
        return

    pairs, skipped = pair_with_labels(
        images_root=images_root,
        labels_root=labels_root,
        image_paths=image_paths,
        skip_existing=args.skip_existing,
    )

    if not pairs:
        print("All images already have label files. Nothing to do.")
        return

    print(
        f"Running detector '{args.model}' over {len(pairs)} images"
        f" (skipped {skipped} existing labels)."
    )

    paired_results = run_detector(
        model_name=args.model,
        pairs=pairs,
        conf=args.conf,
        iou=args.iou,
        device=args.device,
        batch=args.batch,
        enhancements=args.enhance,
        gamma_value=args.gamma_value,
        sharpen_strength=args.sharpen_strength,
    )

    written, empty = write_labels(paired_results, class_id=args.class_id)

    print(f"Generated labels for {written} images. {empty} files contain no detections.")


if __name__ == "__main__":
    main(sys.argv[1:])
