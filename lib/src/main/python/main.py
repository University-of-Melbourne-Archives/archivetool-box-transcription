import cv2
from ultralytics import YOLO
from docaligner import DocAligner

# Load models
detector = YOLO("yolov8n.pt")
corner_finder = DocAligner()

# Load image
image_path = "/content/drive/MyDrive/Box-Data/Flat-Photos/PXL_20251127_230150462.jpg"
image = cv2.imread(image_path)
h_img, w_img, _ = image.shape

# Stage 1: Detect all boxes
results = detector(image)

# Stage 2: Process corners for each detection
for i, box in enumerate(results[0].boxes):
    x1, y1, x2, y2 = map(int, box.xyxy[0])

    # Apply padding to ensure edges are fully inside the crop
    pad = 20
    x1_buf = max(0, x1 - pad)
    y1_buf = max(0, y1 - pad)
    x2_buf = min(w_img, x2 + pad)
    y2_buf = min(h_img, y2 + pad)

    # Extract sub-region
    cropped_box = image[y1_buf:y2_buf, x1_buf:x2_buf]

    # Get 4 corners relative to the crop
    relative_corners = corner_finder(cropped_box)

    # Check if relative_corners is not empty before proceeding
    if relative_corners.size > 0:
        # Map back to global image coordinates
        absolute_corners = relative_corners + [x1_buf, y1_buf]

        print(f"Box {i} corners: {absolute_corners}")

        # Draw geometry on master image
        pts = absolute_corners.astype(int)
        for j in range(4):
            cv2.line(image, tuple(pts[j]), tuple(pts[(j + 1) % 4]), (0, 255, 0), 2)
            cv2.circle(image, tuple(pts[j]), 5, (0, 0, 255), -1)
    else:
        print(f"Box {i}: No corners detected in the cropped region.")

cv2.imwrite("all_boxes_corners_detected.jpg", image)