# EquipX device detector (Phase 5)

Trains a YOLO nano detector for the 10 devices in `classes.yaml` (same order as `content/index.json`). Training and TFLite export run in **Google Colab** (free T4); nothing heavy runs on this Mac.

## Files
- `classes.yaml`: class ids + mapping from each public dataset's labels to our ids (unmapped classes are dropped).
- `train_colab.ipynb`: the whole pipeline (download, merge, train, evaluate, export).
- `merge.py`: the merge/remap/dedupe/split logic (also embedded in the notebook; runnable locally on a downloaded dataset: `python merge.py --raw datasets/raw --out datasets/equipx --own own_photos [--whole-image-boxes]`).
- `meta.py`: lightweight Roboflow metadata check (license, counts, classes; no images). `datasets_report.md` is its summary.
- `.env` (gitignored): `ROBOFLOW_API_KEY=...` for local scripts only. Colab uses Secrets instead.

## Steps
1. Take own photos (50-100 per device, varied angle/light/background; weakest classes: insulation_tester, lcr_meter, analog_multimeter, digital_clamp_meter, soldering_station). Put them in Google Drive: `MyDrive/equipx/own_photos/<class_id>/*.jpg` (class ids = folder names, e.g. `lcr_meter`). Optionally add YOLO `.txt` labels next to a photo; otherwise the notebook puts one near-full-image box (`WHOLE_IMAGE_BOXES = True`, so shoot close-ups of a single device). About 60% of own photos go to the test split.
2. Open https://colab.research.google.com > Upload > `ml/train_colab.ipynb` (or open it from GitHub).
3. Runtime > Change runtime type > T4 GPU.
4. Key icon (Secrets) > add `ROBOFLOW_API_KEY`, enable Notebook access.
5. Runtime > Run all (approve the Drive prompt). About 30-60 minutes. If the session drops, re-run and use the `resume=True` line in the train cell.
6. The last cell downloads `equipx_export.zip` and copies it to `MyDrive/equipx/export/`. Unzip and put `equipx_detector.tflite`, `labels.txt`, `model_info.json` into `ml/export/` (optionally `equipx_detector_int8.tflite`, `metrics.json`).

## Reading the results
The notebook prints a per-class count table (watch the `< 100 images` warnings) and a per-class metrics table on the test split: precision (detections that are right), recall (devices found), mAP50. Target mAP50 above 0.8 per class on a test split containing your own photos. If the test split has no own photos, the notebook says so: scores then reflect public data, not your lab. `model_info.json` records input size, output tensor shape/layout, box units and class order for `TfliteDetector`.

## Output format (10 classes, imgsz 640, no NMS)
Input `[1,3,640,640]` float32 NCHW (planar R, then G, then B), RGB, pixel/255, letterboxed to 640 with grey 114. (`model_info.json` `input.layout` says `NCHW` or `NHWC`; the app handles both.) Output `[1,14,8400]` (4 box rows cx,cy,w,h + 10 class scores, already sigmoid, no objectness). Output `layout` is `channels_first` (or `anchors_first`), `box_units` is `normalized` or `pixels`; both are written to `model_info.json`. Apply score threshold and class-wise NMS in app code.

## Optional: review/merge in the Roboflow web UI instead
Fork the datasets into your Roboflow workspace, use Dataset > Classes to rename/merge to the 10 ids (and drop others), delete bad images, add your photos with the annotation tool, generate a version, and export "YOLOv8". Then in the notebook replace the download/merge cells with a single download of that version and point `data.yaml` at it.

## Local env (optional)
`uv venv --python 3.11 .venv && uv pip install --python .venv/bin/python -r requirements.txt`
