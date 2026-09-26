# Basic design v0.1.0

Architecture: MainActivity -> CaptureService -> ImageReader/MediaProjection -> YoloDetector -> DetectionStore -> MainActivity dashboard.

MainActivity owns user controls and dashboard rendering. CaptureService is a foreground service and owns MediaProjection, frame acquisition, lifecycle, and cumulative label statistics. YoloDetector owns letterbox preprocessing, ONNX Runtime inference, NMS-free output decoding, coordinate restoration, and preview overlay generation. DetectionStore is in-process shared state between the service and activity.

The ONNX model is exported in CI from the official `yolo26n.pt` checkpoint with dynamic input shape and `nms=False`, yielding rows `[x1,y1,x2,y2,confidence,class_id]`. Selected input size is applied per frame without restarting capture.

Failure behavior: denied projection leaves the app stopped; model/capture/inference failures are surfaced in the dashboard and the service stops when capture cannot continue.
