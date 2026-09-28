from pathlib import Path

import cv2 # OK as part of the ultralytics import anyway
import numpy as np
from ultralytics import YOLO


def orderCorners(quad):
    """
    Returns an ordered set of points per quadralateral. Starts from the Top-Left and moves clockwise.

    @Params:
        - quad : 
    """
    centre = quad.mean(axis=0)
    angles = np.arctan2(quad[:, 1] - centre[1], quad[:, 0] - centre[0])
    quad = quad[np.argsort(angles)]  # clockwise on screen, since image y points down
    return np.roll(quad, -np.argmin(quad.sum(axis=1)), axis=0)


def getCornerPoints(polygon):
    """
    Gets corners of polygons (not-axis aligned) by smoothing contours to 4 points.

    @Params:
        - polygon :  
    """
    hull = cv2.convexHull(polygon.astype(np.float32))
    perimeter = cv2.arcLength(hull, True)

    # 
    for eps in np.linspace(0.01, 0.1, 10):
        approx = cv2.approxPolyDP(hull, eps * perimeter, True)
        if len(approx) == 4:
            return orderCorners(approx.reshape(4, 2))
        if len(approx) < 4:
            break

    # return ordered for continuity
    return orderCorners(cv2.boxPoints(cv2.minAreaRect(hull)))


def segArchiveImage(model: YOLO, imageDir: str, conf: float =0.5, includeMask: bool =False):
    """
    Segments an Image into Box & Sticker polygons and returns detections as quadrilaterals.

    Args:
        model (YOLO): Initialised YOLO model as used in training
        imageDir (str) : Path to image for segmentation
        conf (float): Floor confidence for returned segments
        includeMask(bool): Enables MaskImage output  
    """
    detections = {}

    # Segment Image using box confidence
    results = model(imageDir, stream=True, conf=conf, retina_masks=True)

    for result in results:
        boxes = []
        if result.masks is not None:

            # access scores from process results
            scores = result.boxes.conf.cpu().numpy()
            for polygon, score in zip(result.masks.xy, scores):
                # Ignore non-quadrilaterals and above
                if len(polygon) < 3:
                    continue

                # set box details
                box = {
                    "corners": getCornerPoints(polygon).round(1).tolist(),
                    "confidence": float(score),
                }

                if includeMask:  # performance enhanving only; used if polygons are large and not needed exactly downstream
                    box["mask"] = polygon.round(1).tolist()
                
                boxes.append(box)
        
        detections[Path(result.path).name] = boxes
    return detections

def drawDetections(imageDir, detections, outDir):
    # DEBUG ONLY -- used for corner drawing validation
    outDir = Path(outDir)
    outDir.mkdir(parents=True, exist_ok=True)


    for name, boxes in detections.items():
        image = cv2.imread(str(Path(imageDir) / name))

        # Scale line width with resolution, otherwise 1px lines vanish on phone photos
        thickness = max(2, image.shape[1] // 600)

        for i, box in enumerate(boxes):
            if "mask" in box:
                cv2.polylines(image, [np.array(box["mask"], np.int32)], True, (255, 200, 0), thickness)

            corners = np.array(box["corners"], np.int32)
            cv2.polylines(image, [corners], True, (0, 255, 0), thickness)

            for j, (x, y) in enumerate(corners):
                # TL in magenta so corner ordering is visible at a glance
                colour = (255, 0, 255) if j == 0 else (0, 0, 255)
                cv2.circle(image, (int(x), int(y)), thickness * 3, colour, -1)
            
            cv2.putText(image, f"{i} {box['confidence']:.2f}", (int(corners[0][0]) + 5, int(corners[0][1]) + 30),
                        cv2.FONT_HERSHEY_SIMPLEX, thickness * 0.5, (0, 0, 255), thickness)
            
        cv2.imwrite(str(outDir / name), image)


# TEST-HARNESS CODE
test_path = r"C:\Users\ulida\OneDrive - The University of Melbourne\Documents\_Uni\2026 Sem 2\IT Proj\Model-training-checkpoint\Box-Data\Test-Photos"
test_out_path = r"lib\src\main\python\model-sandbox\debug-output"

model = YOLO(r"lib\src\main\python\model-sandbox\weights\yolo26n-archive-boxes-14-09.pt")

detections = segArchiveImage(model, test_path, includeMask=True)
drawDetections(test_path, detections, test_out_path)