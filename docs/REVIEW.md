# ArchiveTool Review Interfaces — Usage Guide With Examples

ArchiveTool provides an optional review stage for the output of the `preview create` command model and another for the output of the `ocr create` command model.

## 1. Review GUI 1 — Segmentation

### 1.1 Purpose

The user interface called by the `preview review` command is used for manual review of the `preview create` output file.
 
Review consists of adjusting any hallucinated, missed, or misaligned box/sticker segmentations.

### 1.2 Interface Overview
 
Resizing the window increases the size of the image viewport; it does not affect the current image scale.
 
#### 1.2.1 Image Controls

Clicking within the boundaries of a box or its sticker will create a selection for both the sticker and box itself. 
One selection may be active at once.

Mouse dragging a corner of the selection of a box or sticker allows the user to move vertices around to change the shape and size of the box.

Mouse dragging within a box allows moving of the box and its sticker. Creates a selection, if not already present.

Mouse dragging within just a sticker allows moving of just the sticker. Creates new selection, if not already present.

Mouse dragging outside the boundaries of any box allows for the panning of the image.

#### 1.2.2 Scaling Controls

| Button | Purpose | Behaviour Details |
|---|---|---|
| `+` | Zooms in on image | - |
| `-` | Zoom out on image | - |
| `Fit` | Adjusts zoom and view position to fit image | Resizes to current viewport size, recommended after resizing window |

#### 1.2.3 Review Controls

| Button | Purpose | Behaviour Details |
|---|---|---|
| `Previous` | Starts review previous image in batch | Does not automatically save unsaved changes |
| `Next` | Starts review for next image in batch | Does not automatically save unsaved changes |
| `Confirm` | Saves all changes made to current image | All additions or modifications to image are reverted if not saved |
| `Create` | Creates a new, rectangular segmentation in the middle of the image to be adjusted| Newly created segmentation is automatically selected and must be confirmed to save creation.
| `Delete` | Deletes currently selected segmentation | Only functional if a selection is active, does not require a save to confirm deletion |
| `Finish` | Finishes the review | Closes review window, does not write unsaved changes |

### 1.3 Output

Updates the segmentation_data.json file in the specified `[-i DIR]` for all images that were changed in the batch.

### 1.4 Example Workflow

In this scenario, the user adjusts the segmentations with any inaccurate points for every image in the batch. 

1. `preview review` is called and the interface loads.
2. User clicks `Next` button until a coloured outline drawn on the image preview does not correctly match its box or sticker edges
3. User clicks within the inaccurate outline to select it.
4. User drags the vertices of the inaccurate sticker and/or box selection to match image.
5. User clicks `Confirm` button to save all changes.
6. User repeats steps 2 to 5 until all images in the batch have been reviewed.
7. User clicks `Finish` button.

## 2. Review GUI 2 — OCR

### 2.1 Purpose

The user interface called by the `ocr review` command is used for manual review of the `ocr create` output file.
 
Review consists of adjusting any missed or inaccurately read box and sticker text.

### 2.2 Interface Overview

Resizing the window increases the size of the image viewport; it does not affect the current image scale.

#### 2.2.1 Image Controls

Mouse dragging anywhere allows for the panning of the image.

#### 2.2.2 Scaling Controls

| Button | Purpose | Behaviour Details |
|---|---|---|
| `+` | Zooms in on image | - |
| `-` | Zoom out on image | - |
| `Fit` | Adjusts zoom and view position to fit image | Resizes to current viewport size, recommended after resizing window |

#### 2.2.3 Input & Formatting

The OCR output is formatted as 3 fields: Node Title, Description, and Date Range.

The single-line input fields are used to review and edit each of the OCR output fields.

The text below each input field is a live multi-line display of the entire line. 

Any input field may be saved while empty.

##### 2.2.3.1 Node Title

\<Record Set ID> is the unique identifier displayed on each archive box sticker that comes in various different formats, given in examples below.

\<Unit Number> is a singular number stored on each archive box sticker. On save, \<Unit Number> is automatically padded to four characters.

*The accepted format is:*
\<Record Set ID> Unit \<Unit Number>

*Examples of valid formatting:*\
1976.0276 Unit 1071\
76/29 Unit 1071\
UMA-AC-000000330 Unit 431\
UMA-SRE-20160038 Unit 0431

##### 2.2.3.2 Description

Any input format is valid.

##### 2.2.3.2 Date Range

The start and end dates are displayed on the box.

In the case of only a singular year being on the box, a singular year should be input accordingly. 

*Accepted format is:*\
yyyy-yyyy;yyyy-MM-dd;yyyy-MM-dd\
*OR*\
yyyy;;

Input must be a possible date and year range.

#### 2.2.4 Review Controls

| Button | Purpose | Behaviour Details |
|---|---|---|
| `Previous` | Starts review previous image in batch | Does not automatically save unsaved changes |
| `Next` | Starts review for next image in batch | Does not automatically save unsaved changes |
| `Save` | Saves all changes made to fields for the current image | All changes to fields are reverted if not saved; an error popup with a targeted message for all saved fields that are invalid is shown.
| `Finish` | Finishes the review | Closes review window, does not write unsaved changes |

### 2.3 Output

Updates the segmentation_data.json file in the specified `[-i DIR]` for all of the box OCR output that was changed in the batch.

### 2.4 Example Workflow

In this scenario, the user adjusts the any inaccurately extracted text for every box in the batch. 

1. `ocr review` is called and the interface loads.
2. User clicks `Next` button until the extracted text for any of the three fields do not correctly match what is read on the box.
3. User clicks on input field(s) with inaccurate text and corrects it.
4. User clicks `Save` button to save all changes.
5. User repeats steps 2 to 4 until all boxes in the batch have been reviewed.
6. User clicks `Finish` button.