# Test plan v0.1.1

- TEST-01 / REQ-12: CI unit test and debug APK build complete successfully.
- TEST-02 / REQ-11: CI verifies a non-empty generated yolo26n.onnx before Android build.
- TEST-03 / REQ-03: Manual device test: display known COCO objects and confirm boxes, labels, and confidence are drawn directly over them on screen.
- TEST-04 / REQ-04,10: Manual device test: switch to another app while Live Mode runs; confirm overlay remains visible and underlying app touch input still works.
- TEST-05 / REQ-05: Confirm overlay HUD updates FPS, inference ms, input size, and object count.
- TEST-06 / REQ-06: Change 320/416/512/640 while running and confirm live overlay continues without restart.
- TEST-07 / REQ-07: Change confidence threshold and confirm visible detections change accordingly.
- TEST-08 / REQ-08,09: Confirm dashboard timing and per-label metrics continue updating.
- TEST-09: Stop Live Mode and confirm overlay, MediaProjection, and foreground notification disappear.

Real-device visual alignment, performance, and correctness require device evidence and are not asserted by CI.
