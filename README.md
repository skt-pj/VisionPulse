# VisionPulse

Android prototype for live on-device object detection. It captures the device screen with MediaProjection, runs YOLO26n through ONNX Runtime, and exposes a dashboard for resolution, confidence threshold, latency/FPS, detection preview, and per-label statistics.

## Initial scope

- Live screen capture
- YOLO26n COCO-80 detection
- Runtime input sizes: 320 / 416 / 512 / 640
- Confidence threshold control
- Inference/preprocess/postprocess/pipeline timing and processed FPS
- Detection preview and per-label counters/confidence statistics

The CI workflow exports the official `yolo26n.pt` checkpoint to dynamic ONNX (`nms=False`) before building the APK.

Version: 0.1.0 (1)
