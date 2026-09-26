# Requirements v0.1.1

Primary concept: VisionPulse is a live visual verification tool. The essential behavior is to show YOLO26n detections directly over the screen being observed so the user can immediately judge what the model is detecting, where, and with what confidence. Performance statistics are secondary diagnostics.

- REQ-01: Acquire the Android screen continuously after MediaProjection user consent.
- REQ-02: Run YOLO26n object detection on captured frames on-device.
- REQ-03: Live Mode shall render each current detection directly over the visible Android screen as a bounding box with class label and confidence.
- REQ-04: The overlay shall remain visible above other apps while Live Mode is running and shall not consume touch input.
- REQ-05: Live Mode shall include a compact on-screen HUD containing YOLO input size, processed FPS, inference latency, and current object count.
- REQ-06: Allow live selection of YOLO input resolution: 320, 416, 512, or 640 square pixels.
- REQ-07: Allow live confidence-threshold adjustment.
- REQ-08: Show inference, preprocessing, postprocessing, pipeline latency, processed FPS, frame count, and current detection count in the app dashboard.
- REQ-09: Retain a secondary in-app detection preview and per-label statistics for later comparison.
- REQ-10: Continue detection while another app is foregrounded, using an Android foreground service.
- REQ-11: Package YOLO26n in the APK as ONNX exported with dynamic input and NMS-free end-to-end output.
- REQ-12: Produce an installable debug APK in CI.

Non-functional: minSdk 26; target/compileSdk 35; Java 17; ONNX Runtime Android. Exact mAP is out of scope without ground-truth data. Visual correctness is assessed primarily through the real-time screen overlay.
