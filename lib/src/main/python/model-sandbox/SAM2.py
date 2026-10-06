import cv2
import torch
from sam2.build_sam import build_sam2
from sam2.automatic_mask_generator import SAM2AutomaticMaskGenerator

# Load model
device = "cuda" if torch.cuda.is_available() else "cpu"
sam2_checkpoint = "checkpoints/sam2.1_hiera_large.pt"
model_cfg = "configs/sam2.1/sam2.1_hiera_l.yaml"
sam2_model = build_sam2(model_cfg, sam2_checkpoint, device=device)

detector = SAM2AutomaticMaskGenerator(
    sam2_model,
    points_per_side=32,  # denser grid = catches smaller/thinner boxes
    pred_iou_thresh=0.86,
    stability_score_thresh=0.92,
    min_mask_region_area=2000,  # filters out label-sticker/tape sized fragments
)

# Load image
image_path = "/content/drive/MyDrive/Box-Data/Flat-Photos/PXL_20251127_230150462.jpg"
image = cv2.imread(image_path)
image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
h_img, w_img, _ = image.shape

# Stage 1: Detect all boxes (SAM2 automatic segmentation)
masks = detector.generate(image_rgb)
print(f"SAM2 found {len(masks)} candidate regions")

# Stage 2: Draw a bounding box for each detected region
for i, mask_data in enumerate(masks):
    x, y, w, h = map(int, mask_data["bbox"])  # SAM2 returns XYWH
    x1, y1, x2, y2 = x, y, x + w, y + h

    print(
        f"Box {i}: ({x1}, {y1}) -> ({x2}, {y2})  "
        f"[score={mask_data['predicted_iou']:.2f}]"
    )

    cv2.rectangle(image, (x1, y1), (x2, y2), (0, 255, 0), 2)
    cv2.putText(
        image,
        str(i),
        (x1 + 4, max(15, y1 + 20)),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.7,
        (0, 0, 255),
        2,
    )

cv2.imwrite("all_boxes_corners_detected.jpg", image)
