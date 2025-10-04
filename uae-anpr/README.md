# UAE OCR ANPR

Spring Boot service that reads UAE license plates using **OpenCV** preprocessing + **Tess4J (Tesseract)** OCR.  
No GPU, no deep learning detector.

## Run (Dev)
1. Install JDK 21 + Maven.
2. Download Tesseract languages `eng.traineddata` and `ara.traineddata` and place them under `tessdata/`.
3. Build & run:
```bash
mvn -q -DskipTests package
java -jar target/uae-ocr-anpr-0.0.1-SNAPSHOT.jar
```
4. Call the API:
```bash
curl -X POST http://localhost:9090/api/v1/plates/recognize   -F "image=@/path/to/plate.jpg"
```

## API
- `POST /api/v1/plates/recognize` (multipart)
- Response:
```json
{
  "results": [
    {"number":"97344","letter":"F","emirate":"Dubai","rawText":"..."}
  ]
}
```

## Notes
- The service attempts to auto-detect a likely plate rectangle via contour heuristics. If none is found, it OCRs the whole image as a fallback.
- Edit `application.yml` to change tessdata path or OCR language.
