from ultralytics import YOLO
import torch

if __name__ == '__main__':
    model_path = r"C:\Users\ulida\Documents\archives-model-training\weights\yolo26n-package-seg-31-08.pt"
    model = YOLO(model_path)

    print("Initializing optimized safe overnight training run...")
    results = model.train(
        data="storeboxes.ndjson",    
        epochs=40,                  
        imgsz=512,                 
        batch=-1,                   
        amp=True,                   
        cache=False,                # Disabled to protect my 16GB System RAM
        patience=8,                 # Stop condition
        workers=2,                  # Reduced to 2 to prevent hard drive read bottlenecking
        device=0,                   
        project="archives_runs",    
        name="storeboxes_segmentation"
    )
