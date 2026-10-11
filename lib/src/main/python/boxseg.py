import ast
from functools import lru_cache
from pathlib import Path

import cv2  # now a direct dependency; opencv-python-headless is enough
import numpy as np
import onnxruntime as ort

BOX_CLASS = "Box"
STICKER_CLASS = "Sticker"
DEFAULT_MODEL_PATH = r"lib\src\main\python\model-sandbox\weights\yolo26n-archive-boxes-14-09.onnx"


def getBoxes(imageDir: str, modelPath: str = DEFAULT_MODEL_PATH, conf: float =0.5, includeMask: bool =False):
    """
    Segments an Image into Box & Sticker polygons and returns detections as quadrilaterals.

    Args:
        imageDir (str) : Path to image for segmentation
        modelPath (str): Path to the exported ONNX model
        conf (float): Floor confidence for returned segments
        includeMask(bool): Includes raw mask points in the output

    Returns:
        detections (dict): JSON style dictionary with formatted image data and all detected objects within
    """
    # Segment Image using box confidence
    labels, scores, polygons = runModel(imageDir, modelPath, conf)
    vertexes = {}

    # Ignore non-quadrilaterals and above
    valid = [i for i in range(len(labels)) if len(polygons[i]) >= 3]
    boxIdx = sorted((i for i in valid if labels[i] == BOX_CLASS), key=lambda i: scores[i], reverse=True)

    # Key every box as box-N in confidence order
    boxQuads = []
    for n, i in enumerate(boxIdx, start=1):
        box, quad = toSegment(polygons[i], scores[i], includeMask)
        vertexes[f"box-{n}"] = {"box": box, "sticker": []}
        boxQuads.append((f"box-{n}", quad))

    for i in valid:
        if labels[i] != STICKER_CLASS:
            continue
        sticker, quad = toSegment(polygons[i], scores[i], includeMask)
        centre = quad.mean(axis=0)

        # Boxes are in confidence order, so a sticker overlapping two boxes goes to the more confident one
        for boxId, boxQuad in boxQuads:
            if insideQuad(centre, boxQuad):
                vertexes[boxId]["sticker"].append(sticker)
                break

    detections = {Path(imageDir).name: {"vertexes": vertexes}}
    return detections


@lru_cache(maxsize=None)
def loadModel(modelPath: str):
    """
    Helper: Loads an ONNX model once per path, with the class names and input size stored at export.

    Args:
        modelPath (str): Path to the exported ONNX model

    Returns:
        session (ort.InferenceSession): Loaded model
        names (dict): Class index to class name, e.g. {0: 'Box', 1: 'Sticker'}
        imgsz (list[int]): Model input size as [height, width]
    """
    # Cached so repeated calls from Kotlin don't reload the model for every image
    session = ort.InferenceSession(modelPath, providers=["CPUExecutionProvider"])
    meta = session.get_modelmeta().custom_metadata_map
    return session, ast.literal_eval(meta["names"]), ast.literal_eval(meta["imgsz"])


def letterbox(image, inH, inW):
    """
    Helper: Resizes an image to fit the model input while keeping aspect ratio, padding the rest grey.

    Args:
        image (np.ndarray): BGR image as loaded by cv2
        inH (int): Model input height
        inW (int): Model input width

    Returns:
        padded (np.ndarray): Letterboxed image
        r (float): Resize ratio applied to the original image
        left, top (int): Padding offsets, needed to map predictions back
        newW, newH (int): Size of the resized image inside the padding
    """
    h, w = image.shape[:2]
    r = min(inH / h, inW / w)
    newW, newH = round(w * r), round(h * r)
    padX, padY = (inW - newW) / 2, (inH - newH) / 2

    # Rounding matches Ultralytics' LetterBox so predictions line up with how the model was trained
    top, bottom = round(padY - 0.1), round(padY + 0.1)
    left, right = round(padX - 0.1), round(padX + 0.1)

    resized = cv2.resize(image, (newW, newH), interpolation=cv2.INTER_LINEAR)
    padded = cv2.copyMakeBorder(resized, top, bottom, left, right, cv2.BORDER_CONSTANT, value=(114, 114, 114))
    return padded, r, left, top, newW, newH


def runModel(imagePath, modelPath, conf):
    """
    Helper: Runs the ONNX model on one image and decodes each detection into a mask outline.

    Args:
        imagePath (str): Path to image for segmentation
        modelPath (str): Path to the exported ONNX model
        conf (float): Floor confidence for returned segments

    Returns:
        labels (list[str]): Class name per detection
        scores (list[float]): Confidence per detection
        polygons (list[np.ndarray]): Mask outline per detection, in original image pixels
    """
    session, names, (inH, inW) = loadModel(modelPath)

    image = cv2.imread(str(imagePath))
    if image is None:
        raise FileNotFoundError(f"Could not read image: {imagePath}")
    h, w = image.shape[:2]

    # BGR -> RGB, HWC -> NCHW, scaled to 0-1 as in training
    padded, r, left, top, newW, newH = letterbox(image, inH, inW)
    blob = np.ascontiguousarray(padded[:, :, ::-1].transpose(2, 0, 1)[None], dtype=np.float32) / 255.0

    outputs = session.run(None, {session.get_inputs()[0].name: blob})

    # Detections are (1, N, 6 + 32) and mask prototypes are (1, 32, H, W), so tell them apart by rank
    preds, protos = sorted(outputs, key=lambda o: o.ndim)
    preds, protos = preds[0], protos[0]
    c, mh, mw = protos.shape

    # Used for detecting incorrect ONNX exports
    if preds.shape[1] != 6 + c:
        raise ValueError(f"Unexpected output shape {preds.shape}; export the model with end2end=True")

    labels, scores, polygons = [], [], []
    for det in preds[preds[:, 4] >= conf]:
        # Box from letterbox space back into original image pixels
        x1, x2 = np.clip((det[[0, 2]] - left) / r, 0, w)
        y1, y2 = np.clip((det[[1, 3]] - top) / r, 0, h)

        # Mask logits are the detection's coefficients applied to the shared prototypes;
        # upsampled to full image resolution, equivalent to retina_masks=True
        logits = (det[6:] @ protos.reshape(c, -1)).reshape(mh, mw)
        logits = cv2.resize(logits, (inW, inH), interpolation=cv2.INTER_LINEAR)
        logits = cv2.resize(logits[top:top + newH, left:left + newW], (w, h), interpolation=cv2.INTER_LINEAR)

        # logit > 0 is sigmoid > 0.5; masks are cropped to their own box as Ultralytics does
        ix1, iy1, ix2, iy2 = int(x1), int(y1), int(np.ceil(x2)), int(np.ceil(y2))
        mask = np.zeros((h, w), np.uint8)
        mask[iy1:iy2, ix1:ix2] = logits[iy1:iy2, ix1:ix2] > 0

        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if not contours:
            continue

        labels.append(names[int(det[5])])
        scores.append(float(det[4]))
        # Largest contour only, so stray specks of mask don't drag the convex hull around
        polygons.append(max(contours, key=cv2.contourArea).reshape(-1, 2).astype(np.float32))

    return labels, scores, polygons


def orderCorners(quad):
    """
    Helper: Returns an ordered set of points per quadralateral. Starts from the Top-Left and moves clockwise.

    Args:
        quad (list[np.array]): list of 4 coordinate points corresponding to drawn segmentation-boxes

    Returns:
        (list[np.array]): ordered Top Left and clockwise
    """
    centre = quad.mean(axis=0)
    angles = np.arctan2(quad[:, 1] - centre[1], quad[:, 0] - centre[0])
    quad = quad[np.argsort(angles)]  # clockwise on screen, since image y points down
    return np.roll(quad, -np.argmin(quad.sum(axis=1)), axis=0)


def getCornerPoints(polygon):
    """
    Helper: Gets corners of polygons (not-axis aligned) by smoothing contours to 4 points.

    Args:
        polygon (list): List of numpy coordinates containing the predicted outline of the segmented object.

    Returns:
        (list[np.array]): size 4 quadrilateral after reducing mask
    """
    # find the smallest bounding polygon containing all the points of the shaded polygon
    hull = cv2.convexHull(polygon.astype(np.float32))

    # return perimeter of bounding polygon
    perimeter = cv2.arcLength(hull, True)

    # iterating on error values (epsilon) to find simplest polygon approximation s.t. corners = 4
    for epsilon in np.linspace(0.01, 0.1, 10):
        # use Douglas-Pecker for vertex reduction
        approx = cv2.approxPolyDP(hull, epsilon * perimeter, closed= True)

        # filter for quadrilaterals
        if len(approx) == 4:
            return orderCorners(approx.reshape(4, 2))

        # fail on triangular approximations (implies DP-approximation failure)
        if len(approx) < 4:
            break

    # return minimum-bounding box in case of failed DP-approximation
    return orderCorners(cv2.boxPoints(cv2.minAreaRect(hull)))


def toPoints(quad):
    """
    Helper: Formats a quadrilateral list of vertices to list of "x", "y" dict items.

    Args:
        quad (list[np.array]): ordered list of 4 numpy coordinates for the bounding box
    
    Return:
        (list[dict]): returns a list of {"x": _, "y": _} dicts containing coordinates from quad
    """
    return [{"x": float(x), "y": float(y)} for x, y in quad]


def insideQuad(point, quad):
    """
    Helper: Checks whether a point lies inside a convex quadrilateral.

    Args:
        point (np.ndarray): (x, y) point to test
        quad (np.ndarray): 4x2 array of ordered corners

    Returns:
        (bool): True if the point is inside the quadrilateral
    """
    # Inside a convex polygon means the point is on the same side of every edge
    edges = np.roll(quad, -1, axis=0) - quad
    rel = point - quad
    crosses = edges[:, 0] * rel[:, 1] - edges[:, 1] * rel[:, 0]
    return bool(np.all(crosses >= 0) or np.all(crosses <= 0))


def toSegment(polygon, score, includeMask):
    """
    Helper: Formats polygon and score to a segment, quad tuple.

    Args:
        polygon (list[np.array]): list of 4 points corresponding to bounding box edges
        score (float): confidence score for the given polygon
    
    Returns:
        segment (dict): formatted dictionary with segment points and confidence score
        quad (list[np.array]): ordered list of 4 segment points from the polygon
    """
    quad = getCornerPoints(polygon)

    # set box details
    segment = {"points": toPoints(quad.round(1)), "confidence": float(score)}

    if includeMask:  # debug only; incudes mask polygon for comparison (in case of misdrawn polygon)
        segment["mask"] = toPoints(polygon.round(1))

    return segment, quad