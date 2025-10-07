# UAE Car Plate Recognition Service

This project exposes a Spring Boot service that performs automatic number plate recognition (ANPR) for UAE style licence plates. The service combines deterministic computer vision operators implemented with OpenCV and a Tesseract OCR backend tailored to the typographic characteristics of local plates.

## Recognition pipeline

The recognition flow executed for both API endpoints is summarised below:

1. **Load and normalise** – the raw image payload is decoded into an OpenCV `Mat`. Images narrower than 640px are upscaled with bicubic interpolation to preserve character detail before later filtering stages.【F:src/main/java/com/uae/anpr/service/preprocessing/ImagePreprocessor.java†L24-L42】
2. **Contrast enhancement** – the image is converted to grayscale, processed with CLAHE to limit histogram over-amplification, smoothed with a bilateral filter and highlighted with a morphological top-hat operator to emphasise plate ridges.【F:src/main/java/com/uae/anpr/service/preprocessing/ImagePreprocessor.java†L44-L66】
3. **Adaptive binarisation** – the enhanced raster is binarised via mean adaptive thresholding followed by morphological closure to consolidate the character foreground for contour analysis.【F:src/main/java/com/uae/anpr/service/preprocessing/ImagePreprocessor.java†L68-L81】
4. **Candidate extraction** – contours are enumerated, filtered by aspect ratio and area heuristics, and candidate regions are cropped from the colour image for downstream OCR.【F:src/main/java/com/uae/anpr/service/preprocessing/ImagePreprocessor.java†L83-L110】
5. **OCR decoding** – every candidate is evaluated using a Tess4J-powered Tesseract engine configured with a whitelist of alphanumeric characters, fixed DPI and numeric mode. Results are normalised and filtered by confidence before returning the best match.【F:src/main/java/com/uae/anpr/service/ocr/TesseractOcrEngine.java†L24-L78】【F:src/main/java/com/uae/anpr/service/pipeline/RecognitionPipeline.java†L34-L48】

## Running locally

1. Install Java 17 and Maven 3.9+.
2. From the repository root run:

   ```bash
   mvn spring-boot:run
   ```

   The service starts on `http://localhost:8080`.

## API usage

Both endpoints respond with the same JSON payload:

```json
{
  "plateNumber": "M12345",
  "confidence": 0.97,
  "accepted": true
}
```

### Recognise from Base64 JSON

```bash
curl -X POST http://localhost:8080/api/v1/anpr/recognize \
  -H "Content-Type: application/json" \
  -d '{"imageBase64": "<base64-encoded-image>"}'
```

### Recognise from uploaded file

```bash
curl -X POST http://localhost:8080/api/v1/anpr/recognize/file \
  -H "Content-Type: multipart/form-data" \
  -F "image=@/path/to/plate.jpg"
```

The multipart variant streams the raw image bytes directly to the pipeline without requiring client-side Base64 encoding.【F:src/main/java/com/uae/anpr/api/OcrController.java†L39-L73】

## Error handling

Invalid images or decoding issues return HTTP 400 with an empty recognition payload. This covers malformed Base64 strings, empty uploads and unreadable files.【F:src/main/java/com/uae/anpr/api/OcrController.java†L39-L73】

## License

See the repository for licensing details.
