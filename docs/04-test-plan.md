# Test plan v0.1.0

- TEST-01 / REQ-10: CI unit test and debug APK build complete successfully.
- TEST-02 / REQ-09: CI verifies a non-empty generated `yolo26n.onnx` before Android build.
- TEST-03 / REQ-01,08: Manual device test: grant projection, switch to another app, confirm frame count continues increasing.
- TEST-04 / REQ-03: Manual device test: change 320/416/512/640 while running and confirm dashboard input size changes without restarting.
- TEST-05 / REQ-04: Manual device test: change confidence threshold and confirm detections respond.
- TEST-06 / REQ-05: Manual device test: confirm all timing/FPS metrics update.
- TEST-07 / REQ-06,07: Manual device test: confirm preview boxes correspond to visible objects and label counters update.
- TEST-08: Stop capture and confirm foreground notification/capture stop.

Real-device performance and detection correctness are not asserted by CI and require device evidence.
