"""Train a YOLO detector for UAE licence plates."""
from __future__ import annotations

import argparse
from pathlib import Path

from ultralytics import YOLO


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fine-tune YOLO for UAE plate detection")
    parser.add_argument("--imgsz", type=int, default=640, help="Input image size")
    parser.add_argument("--epochs", type=int, default=80, help="Training epochs")
    parser.add_argument("--batch", type=int, default=16, help="Training batch size")
    parser.add_argument(
        "--weights",
        type=str,
        default="yolo11n.pt",
        help="Pretrained weights to start from",
    )
    default_data = Path(__file__).with_name("data.yaml").resolve()
    parser.add_argument(
        "--data",
        type=str,
        default=str(default_data),
        help="Dataset YAML path",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    project_dir = Path("runs") / "detect"
    name = "train"

    model = YOLO(args.weights)
    results = model.train(
        data=args.data,
        imgsz=args.imgsz,
        epochs=args.epochs,
        batch=args.batch,
        project=str(project_dir),
        name=name,
        exist_ok=True,
    )

    best_path = Path(results.save_dir) / "weights" / "best.pt"
    metrics = getattr(model.trainer, "metrics", {})

    print("Training complete.")
    if metrics:
        precision = metrics.get("precision", None)
        recall = metrics.get("recall", None)
        map50 = metrics.get("mAP50", None)
        map5095 = metrics.get("mAP50-95", None)
        print("Best metrics:")
        print(f"  Precision: {precision}")
        print(f"  Recall: {recall}")
        print(f"  mAP50: {map50}")
        print(f"  mAP50-95: {map5095}")
    print(f"Best weights saved at: {best_path}")


if __name__ == "__main__":
    main()
