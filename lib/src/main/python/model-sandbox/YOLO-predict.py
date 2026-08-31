from ultralytics import YOLO

# 1. Load trained segmentation-model weights 
model = YOLO(r"weights\yolo26n-storeboxes-seg-31-08.pt")

# 2. Run inference on test images
results = model.predict(
    source=r"C:\Users\ulida\Documents\archives-model-training\Box-Data\Flat-Photos\PXL_20251127_230207837.jpg", 
    conf=0.5,       # Confidence thresholding
    save=True,      # Saved to ./runs/predict
    show_boxes=True # For box visualisation
)

print("Inference completed successfully!")
