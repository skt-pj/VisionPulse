from pathlib import Path
import shutil

from ultralytics import YOLO


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    assets = root / "app" / "src" / "main" / "assets"
    assets.mkdir(parents=True, exist_ok=True)

    model = YOLO("yolo26n.pt")
    exported = Path(
        model.export(
            format="onnx",
            imgsz=640,
            dynamic=True,
            nms=False,
            opset=17,
            simplify=True,
        )
    )
    target = assets / "yolo26n.onnx"
    shutil.copy2(exported, target)
    print(target)


if __name__ == "__main__":
    main()
