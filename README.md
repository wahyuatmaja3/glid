# Glid

Glid is a Java desktop MVP for an offline face-recognition attendance system based on `architecture.md`.

## Stack

- Java 21
- JavaFX
- Maven
- SQLite
- OpenCV

## Implemented MVP Scope

- desktop dashboard
- employee registration form
- generated face embedding placeholders
- simulated realtime recognition pipeline
- OpenCV realtime camera preview
- OpenCV realtime face detection overlay
- face evidence frame and crop saving from live camera
- throttled evidence saving and auto attendance trigger
- simple local embedding extraction and similarity-based recognition
- duplicate attendance cooldown logic
- local SQLite data store following the suggested schema
- attendance history viewer
- CSV-style report preview for export workflow

## Current Limitations

- face tracking and mask handling are simulated
- ONNX Runtime, Excel export, and PDF export are not wired yet
- no authentication/role management yet

## Local Database

- SQLite file: `data/glid.db`
- schema auto-created on startup
- demo seed data inserted only when database is empty

## Run

```bash
mvn javafx:run
```

## OpenCV Notes

- default camera index is `0`
- native library is loaded via the `org.openpnp:opencv` package
- if camera fails to open, try another device index such as `1`
- current integration provides live preview and Haar cascade face detection overlay
- when at least one face is detected, the app stores:
  - full evidence frame in `evidence/`
  - largest detected face crop in `evidence/crops/`
- evidence saving is throttled to once every 2 seconds
- optional auto attendance mode triggers simulated recognition from the latest detected face crop
- registration uses the latest detected face crop when available to generate local embeddings
- recognition now compares the latest detected face crop against stored local embeddings using cosine similarity
- auto attendance trigger is throttled to once every 4 seconds, and final duplicate prevention still uses the attendance cooldown service
- set `GLID_OPENCV_CASCADE` to the full XML path if the cascade file is not in a default location
- fallback cascade search paths:
  - `assets/haarcascade_frontalface_default.xml`
  - `data/haarcascade_frontalface_default.xml`
  - `C:/opencv/sources/data/haarcascades/haarcascade_frontalface_default.xml`
  - `C:/Program Files/OpenCV/data/haarcascades/haarcascade_frontalface_default.xml`
- recognition still uses simulated pipeline
- this is a lightweight MVP recognizer based on grayscale block features, not ArcFace/InsightFace yet
- simulated recognition now reuses the latest saved face crop path when available

## Build

```bash
mvn compile
```
