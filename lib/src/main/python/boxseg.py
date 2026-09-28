import os
import numpy as np
from ultralytics import YOLO

"""
Basic Skeleton for interactions with the model
"""

# TODO: run tests on box drawing with updated Archive-trained Model
# TODO: update sourceDir to imgPaths as an Array of Strings

# Keyed by weights path so different checkpoints (e.g. seg-package vs
# StoreBoxes fine-tune) can be swapped without reloading unnecessarily
_MODEL_CACHE = {}

def getBoxes(files : list[str], weights : str, conf : float, sticker : bool):
    """
    Params:
    files: list of str, defines the list of files for predictions
    weights: path_to_weights
    conf: confidence of prediction


    1. Loads model
    2. Load File Path
    3. Predict on files
    4. Extract Info into a python dict
    5. Return as a dictionary
    """
    

    # RETURNS: JSON as a python dict
    # TODO: Make a stub that returns the python dict with empty vals in the right format
    pass

def predictSegments():
    """
    Runs model on a list of segments to a given conf interval.
    """

    pass

def filterSegment():
    """
    Filters list of predictions to constrain 1-box, 1-sticker rule by:
        1. remove boxes with overlapping centroids (choose higher conf)
        2. remove multiple stickers in box (choose higher conf)
        3. remove from a buffer from edge
        4. add stickers in hole if none are detected in a box
    """
    pass

# Create a score statistic for boxes?
