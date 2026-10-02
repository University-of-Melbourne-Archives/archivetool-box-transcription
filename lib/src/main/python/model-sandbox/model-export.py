from ultralytics import YOLO

model = YOLO("lib\src\main\python\model-sandbox\weights\yolo26n-archive-boxes-14-09.pt")

model.export(format="onnx")

# IMPORTANT: Cannot int-8 quantized export in /model-sandbox as it requires access to the training data-set for calibration

# Export an INT8-quantized ONNX model with calibration data (for a reduced high-performance alternative if neede)
# model.export(format="onnx", quantize=8, data="boxSeg.yaml")  # creates 'yolo26n_int8.onnx'