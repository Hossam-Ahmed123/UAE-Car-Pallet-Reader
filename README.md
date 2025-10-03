# UAE Car Plate Reader

A Spring Boot 3 (Java 21) REST API for extracting and normalizing UAE car plate numbers from uploaded images. The service now includes an OpenAPI 3 specification with an interactive Swagger UI for easy exploration.

## Features

- Accepts multiple images in a single multipart/form-data request.
- Uses [Tess4J](https://tess4j.sourceforge.net/) as a Java wrapper around the Tesseract OCR engine.
- Applies lightweight grayscale, sharpening, and adaptive-style thresholding before running OCR.
- Normalizes the OCR output by removing noise, duplicates, and Dubai-specific prefixes.
- Provides per-image extraction results including the raw OCR text and the cleaned plate number.

## Processing pipeline

1. **Plate detection** – the default implementation treats the entire image as a candidate plate. Swap the `PlateDetector` bean with a YOLO-based implementation to take advantage of proper localization.
2. **ROI cropping** – each detected bounding box is cropped, ensuring only the plate region reaches OCR.
3. **Image preprocessing** – grayscale conversion, contrast stretching, sharpening, and binary thresholding improve character visibility without requiring native OpenCV bindings.
4. **OCR & post-processing** – Tesseract extracts raw text which is then normalised using regex and emirate-aware heuristics to produce the final plate, city, letters, and digits.

The service scores multiple candidates and returns the most plausible match, mirroring the recommended YOLO → ROI → preprocessing → Tesseract → normalization workflow.

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.8+
- [Tesseract OCR](https://github.com/tesseract-ocr/tesseract) runtime with the `eng` (English) language data installed. On macOS or Linux you can typically install this via your package manager. Make note of the tessdata directory path.

### Configuration

At startup the application attempts to locate the tessdata directory by checking the `tesseract.datapath` property, the `TESSDATA_PREFIX` environment (or system) property, and a few common installation directories for Linux and Windows. If none of these contain the requested language file the application will fail fast with an explicit error message instead of surfacing a low level "Invalid memory access" at request time.

If you installed the trained data files in a non-default location, expose it via the `TESSDATA_PREFIX` environment variable or provide `tesseract.datapath` as a Spring property:

```shell
java -Dtesseract.datapath=/usr/share/tesseract-ocr/5/tessdata -jar target/uae-car-pallet-reader-0.0.1-SNAPSHOT.jar
```

### Build and Run

```shell
mvn clean package
java -jar target/uae-car-pallet-reader-0.0.1-SNAPSHOT.jar
```

The API will be available at `http://localhost:8080`. The generated Swagger UI is served at `http://localhost:8080/swagger-ui.html` and the OpenAPI JSON is available at `http://localhost:8080/v3/api-docs`.

### API Usage

`POST /api/v1/plates/extract`

- **Consumes**: `multipart/form-data`
- **Request Part**: `images` (one or more image files)

Example using `curl`:

```shell
curl -X POST http://localhost:8080/api/v1/plates/extract \ 
  -F "images=@/path/to/maxima.jpg" \ 
  -F "images=@/path/to/lexus.jpg"
```

Sample response:

```json
{
  "results": [
    {
      "fileName": "maxima.jpg",
      "rawText": "F\n97344",
      "normalizedPlate": "F 97344",
      "city": null,
      "characters": "F",
      "number": "97344"
    },
    {
      "fileName": "lexus.jpg",
      "rawText": "Dubai\nBB 19849",
      "normalizedPlate": "BB 19849",
      "city": "Dubai",
      "characters": "BB",
      "number": "19849"
    }
  ]
}
```

### Testing

```shell
mvn test
```

## Notes

- OCR accuracy heavily depends on the quality of the input images. The provided preprocessing is intentionally lightweight to keep latency low; feel free to enhance it (denoising, edge detection, etc.) for production scenarios.
- Tess4J packages native binaries for common platforms. Ensure that your deployment environment includes the required native libraries (Linux x86_64 is supported out of the box).
