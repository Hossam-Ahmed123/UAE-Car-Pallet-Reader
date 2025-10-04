"""Train a YOLO detector for UAE licence plates."""
from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any, Dict

import yaml
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


def load_data_config(data_path: str) -> Dict[str, Any]:
    """Load a Ultralytics data.yaml file and resolve relative paths."""

    config_path = Path(data_path)
    with config_path.open("r", encoding="utf-8") as fh:
        config: Dict[str, Any] = yaml.safe_load(fh)

    dataset_root = config_path.parent.resolve()
    config["path"] = str(dataset_root)

    def resolve_entry(value: Any) -> Any:
        if isinstance(value, str):
            entry_path = Path(value)
            if not entry_path.is_absolute():
                entry_path = (dataset_root / entry_path).resolve()
            return str(entry_path)
        if isinstance(value, list):
            return [resolve_entry(item) for item in value]
        return value

    for key in ("train", "val", "test"):
        if key in config:
            config[key] = resolve_entry(config[key])

    return config


def main() -> None:
    args = parse_args()
    project_dir = Path("runs") / "detect"
    name = "train"

    data_config = load_data_config(args.data)

    model = YOLO(args.weights)
    results = model.train(
        data=data_config,
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
