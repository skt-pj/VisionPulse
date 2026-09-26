# Detailed design v0.1.1

Live Mode data path:

MediaProjection -> ImageReader -> bitmap -> letterbox preprocessing -> YOLO26n ONNX -> detections -> full-screen transparent overlay.

The overlay is a TYPE_APPLICATION_OVERLAY window. It is non-focusable and non-touchable so the underlying app remains fully operable. Detection coordinates are mapped from the captured screen coordinate system to overlay view coordinates. Each detection renders a box, COCO class label, and confidence. A compact HUD renders input size, processed FPS, inference latency, and current detection count.

Only one inference is allowed in flight. acquireLatestImage is used so stale frames are discarded rather than queued. Input resolution can change live between 320/416/512/640 without restarting MediaProjection.

The dashboard remains secondary and provides detailed timing plus cumulative per-label statistics. The primary acceptance path is visual: open arbitrary content, start Live Mode, switch to another app, and confirm boxes/labels follow detected objects on the actual screen.
