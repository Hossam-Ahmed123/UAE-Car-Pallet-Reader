# UAE Car Plate Reader

A Spring Boot 3 (Java 17) REST API for extracting and normalizing UAE car plate numbers from uploaded images.

## Features

- Accepts multiple images in a single multipart/form-data request.
- Uses [Tess4J](https://tess4j.sourceforge.net/) as a Java wrapper around the Tesseract OCR engine.
- Applies lightweight grayscale and contrast preprocessing before running OCR.
- Normalizes the OCR output by removing noise, duplicates, and Dubai-specific prefixes.
- Provides per-image extraction results including the raw OCR text and the cleaned plate number.

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- [Tesseract OCR](https://github.com/tesseract-ocr/tesseract) runtime with the `eng` (English) language data installed. On macOS or Linux you can typically install this via your package manager. Make note of the tessdata directory path.

### Configuration

By default the application expects the Tesseract runtime to be discoverable on the system path. If you installed the trained data files in a non-default location, expose it via the `TESSDATA_PREFIX` environment variable or provide `tesseract.datapath` as a Spring property:

```shell
java -Dtesseract.datapath=/usr/share/tesseract-ocr/5/tessdata -jar target/uae-car-pallet-reader-0.0.1-SNAPSHOT.jar
```

### Build and Run

```shell
mvn clean package
java -jar target/uae-car-pallet-reader-0.0.1-SNAPSHOT.jar
```

The API will be available at `http://localhost:8080`.

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
      "normalizedPlate": "F 97344"
    },
    {
      "fileName": "lexus.jpg",
      "rawText": "Dubai\nBB 19849",
      "normalizedPlate": "BB 19849"
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
