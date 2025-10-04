"""Export the trained YOLO model to ONNX for the Spring Boot service."""
from __future__ import annotations

from pathlib import Path

from ultralytics import YOLO


BEST_PT = Path("runs/detect/train/weights/best.pt")
TARGET_ONNX = Path(__file__).resolve().parent.parent / "service" / "models" / "best.onnx"


def main() -> None:
    if not BEST_PT.exists():
        raise FileNotFoundError(f"Trained weights not found at {BEST_PT}")

    model = YOLO(str(BEST_PT))
    print(f"Exporting {BEST_PT} -> {TARGET_ONNX}")
    model.export(format="onnx", opset=12, imgsz=640, dynamic=False)

    generated = BEST_PT.parent / "best.onnx"
    if not generated.exists():
        raise FileNotFoundError("Ultralytics export did not create best.onnx as expected")

    TARGET_ONNX.parent.mkdir(parents=True, exist_ok=True)
    generated.replace(TARGET_ONNX)
    print(f"ONNX model exported to {TARGET_ONNX}")


if __name__ == "__main__":
    main()
