# UAE ANPR Monorepo

Production-ready training and inference stack for UAE licence plate recognition. The repository is organised as a monorepo with a Python fine-tuning workspace and a Spring Boot inference service that consumes the exported ONNX detector and Tess4J OCR.

```
uae-anpr/
├── training/          # Ultralytics YOLO workflow
└── service/           # Spring Boot REST API + ONNX Runtime
```

## 1. Training workflow (Ultralytics YOLO)

### Dataset layout

Place your raw plate images inside `training/data/images`. The `utils_autosplit.py` helper will move them into `train/` and `val/` splits (85/15) the first time you run it. Labels should be supplied in YOLO format under `training/data/labels/train` and `training/data/labels/val`.

### Environment setup

#### Windows (PowerShell)

```powershell
cd training
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python utils_autosplit.py
yolo task=detect mode=train model=yolo11n.pt data=data.yaml imgsz=640 epochs=80 batch=16
yolo mode=export model=runs/detect/train/weights/best.pt format=onnx opset=12
copy .\runs\detect\train\weights\best.onnx ..\service\models\best.onnx
```

#### Linux/macOS

```bash
cd training
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python utils_autosplit.py
python train_yolo.py
python export_onnx.py
```

The scripted `train_yolo.py` entry point mirrors the YOLO CLI defaults specified above (`--imgsz 640 --epochs 80 --batch 16 --weights yolo11n.pt`). After training completes, `export_onnx.py` moves the exported `best.onnx` into `service/models/best.onnx` for deployment.

## 2. REST inference service (Java 21 / Spring Boot 3)

### Local development

Prerequisites:

* Java 21 (Temurin recommended)
* Maven 3.9+
* The exported detector at `service/models/best.onnx`
* Tessdata files (`eng.traineddata`, `ara.traineddata`) placed in `service/tessdata`

Build and test:

```bash
cd service
mvn -q -DskipTests package
mvn test
```

Run locally:

```bash
java -jar target/anpr-service-1.0.0.jar
```

The API listens on port `9090`. Recognise plates by sending a multipart request:

```bash
curl -X POST http://localhost:9090/api/v1/plates/recognize \
  -F "image=@sample.jpg"
```

On success the service returns:

```json
{
  "results": [
    {
      "number": "97344",
      "letter": "F",
      "emirate": "Dubai",
      "confidence": 0.92,
      "rawText": "optional-ocr-dump",
      "x": 120,
      "y": 210,
      "width": 220,
      "height": 110
    }
  ]
}
```

When no plates are detected the service responds with HTTP 422 and a descriptive error payload.

### Docker build

```bash
cd service
docker build -t uae-anpr-service .
docker run --rm -p 9090:9090 \ 
  -v $(pwd)/models:/app/models \ 
  -v $(pwd)/tessdata:/app/tessdata \ 
  uae-anpr-service
```

The container includes the ONNX model and tessdata directories; mount your trained artefacts at runtime to swap models without rebuilding the image.

## 3. Repository notes

* `service/src/test` contains lightweight unit tests for the OCR normalisation pipeline and emirate parsing heuristics.
* `training/env.txt` summarises how to create/activate virtual environments on Windows and Linux.
* `service/application.yml` exposes configurable thresholds and paths for the runtime.

This solution is self-contained and can operate entirely offline once models and tessdata files are available locally.
