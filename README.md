# Glid

Glid is a Java desktop MVP for an offline face-recognition attendance system based on `architecture.md`.

## Stack

- Java 21
- JavaFX
- Maven
- SQLite

## Implemented MVP Scope

- desktop dashboard
- employee registration form
- generated face embedding placeholders
- simulated realtime recognition pipeline
- duplicate attendance cooldown logic
- local SQLite data store following the suggested schema
- attendance history viewer
- CSV-style report preview for export workflow

## Current Limitations

- face detection, tracking, recognition, and mask handling are simulated
- ONNX Runtime, OpenCV, Excel export, and PDF export are not wired yet
- no authentication/role management yet

## Local Database

- SQLite file: `data/glid.db`
- schema auto-created on startup
- demo seed data inserted only when database is empty

## Run

```bash
mvn javafx:run
```

## Build

```bash
mvn compile
```
