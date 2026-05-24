# Product Requirements Document (PRD)

## Offline Face Recognition Attendance System (Desktop)

---

# 1. Product Overview

## Product Name

**Glid**

## Product Type

Desktop-based offline attendance system using face recognition.

## Objective

Build a fast and accurate offline attendance system where employees can perform attendance simply by walking past a camera without touching any device.

The system should prioritize:

* very fast recognition speed
* high recognition accuracy
* offline operation
* mask-compatible face recognition
* automatic attendance logging
* face evidence storage
* attendance reporting/export

---

# 2. Goals

## Primary Goals

* Detect and recognize faces in realtime
* Record attendance automatically
* Work completely offline
* Support masked face recognition
* Generate attendance reports
* Store captured face evidence

## Secondary Goals

* Minimize false recognition
* Minimize duplicate attendance
* Reduce hardware requirements
* Easy employee registration

---

# 3. Non-Goals (MVP Exclusions)

The following features are excluded from MVP:

* Cloud synchronization
* Mobile application
* Fingerprint integration
* GPS tracking
* Payroll integration
* Shift scheduling
* Multi-branch synchronization
* Face anti-spoofing with dedicated IR hardware
* Online dashboard

---

# 4. Target Users

## Primary Users

* Small to medium offices
* Factories
* Schools
* Warehouses
* Retail stores

## System Operators

* HR/Admin
* Security staff
* Office administrators

---

# 5. Core Features

# 5.1 Realtime Face Attendance

## Description

The system continuously monitors camera feed and automatically records attendance when a known face is detected.

## Requirements

* Realtime face detection
* Realtime face recognition
* Automatic attendance logging
* No user interaction required
* Recognition latency target:

  * under 1 second
* Detect multiple faces simultaneously

## Attendance Data

System stores:

* employee ID
* employee name
* timestamp
* attendance type

  * check-in
  * check-out
* camera/device ID
* captured face image

---

# 5.2 Face Registration

## Description

Admin can register employee faces into the system.

## Registration Flow

1. Admin opens registration page
2. Input employee data
3. Capture multiple face angles
4. Generate face embeddings
5. Save employee profile

## Employee Data

* employee ID
* full name
* department
* position (optional)
* status (active/inactive)

## Registration Requirements

* Minimum 5 captured angles
* Support masked and unmasked registration
* Prevent blurry image registration
* Validate face visibility

---

# 5.3 Mask-Compatible Recognition

## Description

System should still recognize employees wearing masks.

## Requirements

* Use partial-face recognition model
* Focus on:

  * eyes
  * eyebrows
  * upper face landmarks
* Accuracy target:

  * ≥ 90% under normal lighting

---

# 5.4 Offline Operation

## Description

Entire system must operate without internet connection.

## Requirements

* No cloud dependency
* Local database storage
* Local face embedding storage
* Local report generation

---

# 5.5 Attendance Reports

## Description

Admin can generate attendance reports.

## Report Filters

* by employee
* by department
* by date range
* by attendance type

## Export Formats

### Excel (.xlsx)

### PDF Report

## Report Contents

* employee name
* employee ID
* attendance timestamp
* attendance status
* captured face thumbnail

---

# 5.6 Face Evidence Storage

## Description

The system stores captured face images for verification and audit purposes.

## Requirements

* Save face snapshot during attendance
* Link image to attendance log
* View image from report/history
* Compress images automatically

---

# 5.7 Attendance History Viewer

## Description

Admin can review attendance logs.

## Features

* search attendance
* filter by date
* filter by employee
* preview captured face image
* manual delete/edit (admin only)

---

# 6. Performance Requirements

# 6.1 Recognition Speed

## Target

* Face detection:

  * < 300ms
* Face recognition:

  * < 700ms
* Total attendance processing:

  * < 1 second

---

# 6.2 Accuracy

## Target Accuracy

| Condition    | Target |
| ------------ | ------ |
| Normal face  | ≥ 95%  |
| Wearing mask | ≥ 90%  |
| Low light    | ≥ 80%  |

---

# 6.3 Concurrent Detection

## Requirements

* Detect up to 5 faces simultaneously
* Avoid duplicate attendance spam

---

# 7. Technical Requirements

# 7.1 Platform

## Desktop OS Support

* Windows 10+
* Windows 11

Optional future:

* Linux

---

# 7.2 Camera Support

## Supported Input

* USB webcam
* IP camera (RTSP)
* CCTV stream

## Minimum Camera Requirements

* 1080p recommended
* 30 FPS recommended

---

# 7.3 Offline Database

## Recommended Database

* SQLite (MVP)

---

# 7.4 AI/Recognition Engine

## Recommended Technologies

### Face Detection

* RetinaFace

### Face Recognition

* ArcFace
  or
* InsightFace

### Runtime

* ONNX Runtime

---

# 7.5 Desktop Framework

## Recommended Stack

### Core

* Java 21

### Desktop UI

* JavaFX

### Backend/API Layer

* Spring Boot (embedded/local)

### Computer Vision

* OpenCV

---

# 8. Functional Requirements

| ID     | Requirement                                   |
| ------ | --------------------------------------------- |
| FR-001 | System must detect faces in realtime          |
| FR-002 | System must recognize registered faces        |
| FR-003 | System must work offline                      |
| FR-004 | System must support masked recognition        |
| FR-005 | System must register new employees            |
| FR-006 | System must generate attendance reports       |
| FR-007 | System must export Excel reports              |
| FR-008 | System must export PDF reports                |
| FR-009 | System must store attendance face snapshots   |
| FR-010 | System must prevent duplicate attendance spam |

---

# 9. Non-Functional Requirements

| ID      | Requirement                             |
| ------- | --------------------------------------- |
| NFR-001 | Startup time under 10 seconds           |
| NFR-002 | Stable for 24/7 operation               |
| NFR-003 | Works without internet                  |
| NFR-004 | UI responsive on low-end office PCs     |
| NFR-005 | Local data encryption for employee data |

---

# 10. Security Requirements

## Access Roles

### Admin

* register employee
* export reports
* manage system

### Operator

* monitor attendance only

---

# 11. Duplicate Attendance Prevention

## Cooldown Logic

After successful attendance:

* ignore same employee for X minutes
* configurable
* default: 5 minutes

---

# 12. Suggested System Architecture

```text
Camera Feed
    ↓
Face Detection
    ↓
Face Tracking
    ↓
Face Recognition
    ↓
Attendance Validation
    ↓
Attendance Database
    ↓
Report Generator
```

---

# 13. Suggested Database Tables

## employees

* id
* employee_code
* full_name
* department
* status
* created_at

## face_embeddings

* id
* employee_id
* embedding_vector
* mask_version
* created_at

## attendance_logs

* id
* employee_id
* attendance_type
* timestamp
* image_path
* camera_id

---

# 14. Future Features (Post-MVP)

* Anti-spoofing detection
* Mobile notification
* Multi-device synchronization
* Cloud backup
* Payroll integration
* Shift management
* Visitor recognition
* Multi-camera analytics
* Live dashboard
* Face liveness detection

---

# 15. MVP Success Criteria

The MVP is considered successful if:

* recognition speed under 1 second
* recognition accuracy above 90%
* stable offline operation
* successful report exports
* admins can register employees easily
* attendance logs include face evidence
* system runs continuously without crashing for 24 hours+
