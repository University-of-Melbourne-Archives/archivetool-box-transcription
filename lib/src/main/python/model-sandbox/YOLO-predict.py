from ultralytics import YOLO

# 1. Load trained segmentation-model weights
model = YOLO(
    r"C:\Users\ulida\OneDrive\Documents\_Uni\RECS\Github\comp30022-uom-archives\lib\src\main\python\model-sandbox\weights\yolo26n-archive-boxes-14-09.pt"
)

# 2. Run inference on test images
results = model.predict(
    source=r"C:\Users\ulida\Documents\archives-model-training\Box-Data\Flat-Photos\PXL_20251127_230207837.jpg",
    conf=0.5,  # Confidence thresholding
    save=True,  # Saved to ./runs/predict
    show_boxes=True,  # For box visualisation
)

print("Inference completed successfully!")
