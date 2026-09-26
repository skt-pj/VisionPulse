# Requirements v0.1.0

- REQ-01: Acquire the Android screen continuously after MediaProjection user consent.
- REQ-02: Run YOLO26n object detection on captured frames on-device.
- REQ-03: Allow live selection of YOLO input resolution: 320, 416, 512, or 640 square pixels.
- REQ-04: Allow live confidence-threshold adjustment.
- REQ-05: Show inference, preprocessing, postprocessing, pipeline latency, processed FPS, frame count, and current detection count.
- REQ-06: Show a detection preview with bounding boxes, labels, and confidence.
- REQ-07: Show per-label current count, cumulative count, average confidence, and maximum confidence.
- REQ-08: Continue detection while another app is foregrounded, using an Android foreground service.
- REQ-09: Package YOLO26n in the APK as ONNX exported with dynamic input and NMS-free end-to-end output.
- REQ-10: Produce an installable debug APK in CI.

Non-functional: minSdk 26; target/compileSdk 35; Java 17; model inference through ONNX Runtime Android. Exact accuracy metrics such as mAP are out of scope because no ground-truth dataset is supplied; manual correctness evaluation is supported by the preview and confidence statistics.
