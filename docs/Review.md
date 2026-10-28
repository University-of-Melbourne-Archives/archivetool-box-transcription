# ArchiveTool Review Interfaces: Usage Guide With Examples
## Preamble
ArchiveTool provides an optional review stage for the output of both the Segmentation `(preview create)` 
and Transcription `(ocr create)` stages.  
These review stages are powered by simple user interfaces.  
This document serves as a guide on the usage of these review interfaces.  

### Table of Contents
- [Review GUI 1: Segmentation](#review-gui-1-segmentation)
- [Review GUI 2: Segmentation](#review-gui-2-ocr)

## Review GUI 1: Segmentation

### Purpose

The user interface called by the `preview review` command is used for manual review of the `preview create` output file.
 
This interface allows the user to adjust any hallucinated, missed, or misaligned box/sticker segmentations.

### Interface Overview

#### General 
In this interface, the window is resizable.  
Resizing the window increases the size of the image viewport, but does not affect the current image scale.  
The <kbd>Fit</kbd> button can rescale the image to correctly fit the adjusted viewport.

While using this interface, you can use the operating system _close_ button at any time.  
This will close the app and save your progress.  
However, you should always finish your work with the <kbd>Finish</kbd> button, as otherwise, 
edits to the current image may not be saved completely.
 
#### Image Controls

Clicking within the boundaries of a box or its sticker will select both the sticker and the box itself. 
One selection may be active at once.

Clicking and dragging the corner of a selected box or sticker allows the user
to move the vertex around and change the size and shape of the box.

Clicking and dragging within a box will move the box and its sticker.  
This will select the box and sticker, if they are not selected already.

Clicking and dragging within just a sticker allows moving of just the sticker.  
This will select the sticker and its parent box, if they are not selected already.

Clicking and dragging in any area where a box is not present will pan the image.

#### Scaling Controls

| Button         | Purpose                                                              |
|----------------|----------------------------------------------------------------------|
| <kbd>+</kbd>   | Zooms in on the image                                                |
| <kbd>-</kbd>   | Zooms out on the image                                               |
| <kbd>Fit</kbd> | Adjusts the zoom and view position for the image to fit the viewport |

#### Review Controls

| Button              | Purpose                                                      | Behaviour Details                                                                                                                  |
|---------------------|--------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| <kbd>Previous</kbd> | Opens the previous image in the batch for review             | Does not save unsaved changes                                                                                                      |
| <kbd>Next</kbd>     | Opens the next image in the batch for review                 | Does not save unsaved changes                                                                                                      |
| <kbd>Confirm</kbd>  | Saves all of the changes made to the current image           | All additions and modifications to an image are reverted if they are not saved by <kbd>Confirm</kbd>                               |
| <kbd>Create</kbd>   | Creates a new, rectangular border in the middle of the image | This new segmentation is automatically selected and must be saved with the <kbd>Confirm</kbd> button                               |
| <kbd>Delete</kbd>   | Deletes the currently selected segmentation                  | Does nothing when no boxes or stickers are selected<br/>Deletions happen immediately and do not require saving to confirm deletion |
| <kbd>Finish</kbd>   | Finishes the review                                          | Closes the review window and only saves changes made to the current image                                                          |

### Output

Saving updates a file named `.archivetool_generated/job.json` in the target directory.

### Example Workflow

In this scenario, the user reviews the segmentations generated for a directory of images. 

1. The `preview review` command is called from the command-line and the interface loads.
2. The user clicks the <kbd>Next</kbd> button until one or more of the coloured outlines drawn on the image preview
does not correctly align with any boxes or stickers.
3. The user clicks within the inaccurate outline to select it.
4. The user clicks and drags the corners of the inaccurate sticker and/or box until it matches the image.
5. The user clicks the <kbd>Confirm</kbd> button to save the change.
6. The user repeats steps 2 to 5 until all of the images in the batch have been reviewed.
7. The user clicks the <kbd>Finish</kbd> button and the GUI closes.

## Review GUI 2: OCR

### Purpose

The user interface called by the `ocr review` command is used for manual review of the `ocr create` output file.
 
This interface allows the user to adjust any missed or inaccurately transcribed text from a box and its sticker.

### Interface Overview
#### General
In this interface, the window is resizable.  
Resizing the window increases the size of the image viewport, but does not affect the current image scale.  
The <kbd>Fit</kbd> button can rescale the image to correctly fit the adjusted viewport.

While using this interface, you can use the operating system _close_ button at any time.  
This will close the app and save your progress.  
However, you should always finish your work with the <kbd>Finish</kbd> button, as otherwise,
edits to the current image may not be saved completely.

#### Image Controls

Clicking and dragging anywhere on the image will pan it.


#### Scaling Controls

| Button         | Purpose                                                              |
|----------------|----------------------------------------------------------------------|
| <kbd>+</kbd>   | Zooms in on the image                                                |
| <kbd>-</kbd>   | Zooms out on the image                                               |
| <kbd>Fit</kbd> | Adjusts the zoom and view position for the image to fit the viewport |

#### Input and Formatting

The OCR output is formatted as 3 fields: 
- Node Title,
- Description, and 
- Date Range.

The single-line input fields are used to review and edit each of the OCR output fields.[^1]  
The text below each input field is a live multi-line display of the entire line. 

Any input field can be saved while empty or with a value in the expected format.

##### Node Title

*The accepted format for this field is `<Record Set ID> Unit <Unit Number>`, where:*  
- `<Record Set ID>` is the unique identifier for a series of boxes, and is displayed on a box's sticker.  
It can come in various different formats, as given in the examples below.
- `<Unit Number>` is a singular number used to reference the individual box within a series of boxes,
and is displayed on a box's sticker.  
On save, the `<Unit Number>` is automatically padded to four characters.

Any of these lines would be valid formatting:
```text
1976.0276 Unit 1071
76/29 Unit 1071
UMA-AC-000000330 Unit 431
UMA-SRE-20160038 Unit 0431
```

##### Description

Any input text is valid. Use a semi-colon `(;)` to separate different lines.

##### Date Range

This is the range of years that a box is relevant for.  
This field is only automatically populated when there are dates written on the box,
but during review, a range can be added through context.

When there is only a singular date on the box, a singular year should be input accordingly. 

If only years are known, the generated range will be from the 1st of January until the 31st of December.

The following inputs are valid formats:
```text
yyyy-yyyy;yyyy-MM-dd;yyyy-MM-dd

yyyy;
```

Additionally, the input is checked to be a valid date and year range.

#### Review Controls

| Button              | Purpose                                                 | Behaviour Details                                                                                                                                 |
|---------------------|---------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| <kbd>Previous</kbd> | Opens the previous image in the batch for review        | Does not save unsaved changes                                                                                                                     |
| <kbd>Next</kbd>     | Opens the next image in the batch for review            | Does not save unsaved changes                                                                                                                     |
| <kbd>Save</kbd>     | Saves any changes made to a field for the current image | All modifications to a field if they are not saved by <kbd>Save</kbd><br/>If a modification is in an invalid format, an error message will pop up |
| <kbd>Finish</kbd>   | Finishes the review                                     | Closes the review window and does not write any unsaved changes                                                                                   |

### Output
Saving updates a file named `.archivetool_generated/job.json` in the target directory.

### Example Workflow

In this scenario, the user adjusts reviews and corrects the text extracted from each box in a directory of images. 

1. The `ocr review` command is called from the command-line and the interface loads.
2. The user clicks the <kbd>Next</kbd> button until one or more of the three extracted text fields 
do not correctly match what is written on the box.
3. The user clicks on each input field with incorrect text and corrects them.
4. The user clicks the <kbd>Save</kbd> button to save their changes.
5. The user repeats steps 2 to 4 until every box has been reviewed.
6. The user clicks the <kbd>Finish</kbd> button.

[^1]: This input field is single-line due to technical limitations in the GUI library being used.
