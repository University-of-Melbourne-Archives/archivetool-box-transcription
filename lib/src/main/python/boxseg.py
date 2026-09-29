import os
import numpy as np
from ultralytics import YOLO

from pathlib import Path

import cv2 # OK as part of the ultralytics import anyway
import numpy as np
from ultralytics import YOLO

BOX_CLASS = "Box"
STICKER_CLASS = "Sticker"

def getBoxes(model: YOLO, imageDir: list[str], conf: float =0.5, includeMask: bool =False):
    """
    Segments an Image into Box & Sticker polygons and returns detections as quadrilaterals.

    Args:
        model (YOLO): Initialised YOLO model as used in training
        imageDir (str) : Path to image for segmentation
        conf (float): Floor confidence for returned segments
        includeMask(bool): Includes raw mask points in the output

    Returns:
        detections (dict): JSON style dictionary with formatted image data and all detected objects within
    """
    # Segment Image using box confidence
    result = model(imageDir, conf=conf, retina_masks=True, verbose=False)[0]
    vertexes = {}

    if result.masks is not None:

        # access scores from process results
        scores = result.boxes.conf.cpu().numpy()
        labels = [result.names[int(c)] for c in result.boxes.cls.cpu().numpy()]
        polygons = result.masks.xy

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
