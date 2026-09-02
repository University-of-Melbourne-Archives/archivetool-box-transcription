import os
import cv2
import numpy as np
from ultralytics import YOLO

"""
Basic Skeleton for interactions with the model
"""

# TODO: run tests on box drawing with updated Archive-trained Model

# Keyed by weights path so different checkpoints (e.g. seg-package vs
# StoreBoxes fine-tune) can be swapped without reloading unnecessarily
_MODEL_CACHE = {}


def loadModel(weightsPath):
    # Cached so Kotlin can load the model once at Init instead of every call
    if weightsPath not in _MODEL_CACHE:
        _MODEL_CACHE[weightsPath] = YOLO(weightsPath)
    return _MODEL_CACHE[weightsPath]


def detectDirectory(sourceDir, weightsPath, conf=0.05):
    # Main entry point for a batch run: runs box detection on every image
    # in `sourceDir``
    model = loadModel(weightsPath)
    imageExts = {".jpg", ".jpeg", ".png", ".tif", ".tiff", ".bmp"}
    out = {}

    for fname in sorted(os.listdir(sourceDir)):
        fpath = os.path.join(sourceDir, fname)
        if not os.path.isfile(fpath) or os.path.splitext(fname)[1].lower() not in imageExts:
            # Non-image files are still recorded so later runs don't treat
            # them as new/unseen files
            out[fname] = {"is_image": False}
            continue

        results = model.predict(source=fpath, conf=conf, save=False, verbose=False)
        crops = []
        for r in results:
            if r.masks is not None:
                for maskXy in r.masks.xy:
                    # Masks are irregular polygons so we return the smallest enclosing rectangle
                    rect = cv2.minAreaRect(maskXy.astype(np.float32))
                    points = [[float(x), float(y)] for x, y in cv2.boxPoints(rect)]
                    crops.append({"points": points, "type": "box", "transcription": None})
            elif r.boxes is not None:
                # Fallback when no mask is available: use the axis-aligned
                # bounding box corners directly (TL, TR, BR, BL)
                for x1, y1, x2, y2 in r.boxes.xyxy.cpu().numpy():
                    points = [
                        [float(x1), float(y1)],
                        [float(x2), float(y1)],
                        [float(x2), float(y2)],
                        [float(x1), float(y2)],
                    ]
                    crops.append({"points": points, "type": "box", "transcription": None})

        # Crop ids are deliberately not assigned here, since uniqueness has
        # to be tracked across the whole job, not per image
        out[fname] = {"is_image": True, "crops": crops}

    return out


def getCornerPoints(sourceDir, weightsPath, conf=0.05):
    # Coordinates-only view, stripped of type/transcription, for feeding
    # straight into the corner-correction UI without needing the full
    # Job State structure in place yet
    detections = detectDirectory(sourceDir, weightsPath, conf=conf)
    return {
        fname: [crop["points"] for crop in data["crops"]]
        for fname, data in detections.items()
        if data.get("is_image")
    }


def refineCorners(imagePath, cropId, newPoints):
    # Write-back point for the Review step: takes user-adjusted corners
    # and returns them in Job State crop shape so they can be saved back
    if len(newPoints) != 4:
        raise ValueError("A crop must have exactly 4 corner points")
    return {"points": newPoints, "type": "box", "transcription": None}