# Detailed design v0.1.0

Capture uses an RGBA_8888 ImageReader at the device display size. Only one inference is allowed in flight; when inference is busy, newer frames replace queued work through `acquireLatestImage`, preventing an unbounded backlog.

Preprocessing letterboxes the screen bitmap into the selected square input using RGB float32 NCHW normalized to 0..1. Inference runs on a single executor with ONNX Runtime. The expected output is `(1,300,6)`. Rows below the current confidence threshold are dropped. Coordinates are mapped from letterbox coordinates to original screen coordinates.

The preview is rendered at up to 720 px width with detection boxes. Statistics are cumulative for the current service session. FPS is measured from completed inference frames in one-second windows.

State transitions: STOPPED -> user consent -> MODEL_LOADING -> RUNNING -> STOPPED. MediaProjection system revocation also transitions to STOPPED.
