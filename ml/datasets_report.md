# Dataset report (Roboflow API metadata, checked 2026-11-05; no images downloaded)

| name | workspace/project | license (API) | images (project) | latest version (imgs incl. augmentation) | source classes | verdict |
|---|---|---|---|---|---|---|
| tardata | new-workspace-febsz/tardata | CC BY 4.0 | 397 | v15 (954) | 27 (about 15 images each) | use; 14 classes mapped |
| objectdetection | universiti-malaysia-pahang-qcvas/objectdetection-ngxjp | Public Domain | 1018 | v5 (2604) | DC Power Supply 151, Digital Trainer 143, Function Generator 236, Digital Multimeter 160, Oscilloscope 338 | use; all 5 mapped |
| instrument_lab | universidad-de-los-andes-lzihh/instrument-lab-detection | CC BY 4.0 | 228 | v1 (228) | osciloscopio 53, fuente 52, generador 80, multimetro 51 | use; all 4 mapped |
| oscilloscope_detection | roboticslearning/oscilloscope-detection | CC BY 4.0 | 0 | none | Oscilloscope | SKIP: empty project, nothing to download |
| equipment_data | senior-design-yolov7/equipment-data | CC BY 4.0 (was "not checked") | 562 | v1 (741) | AFG 156, ELVIS 89, Digital Oscilliscope 103, Multimeter 109, Power Supply 151 | use; ELVIS mapped to breadboard_trainer (review) |
| testbw | new-workspace-febsz/testbw | CC BY 4.0 | 200 | v8 (520) | 10 (20 each) | use; 4 classes mapped |
| resistor_detection | circuits-project/resistor-detection-5azes | CC BY 4.0 | 756 | v5 (1820) | wire 7599, breadboard 566, resistor 2878 | use; only breadboard mapped |

Notes
- Version image counts include Roboflow augmentations (flips, rotations, ...). merge.py keeps augmented copies of one source image in the same split (grouped by the `.rf.` filename stem), so there is no train/test leakage.
- Rough expected unique-image supply per class (before own photos): digital_oscilloscope, function_generator, dc_power_supply, digital_multimeter, breadboard_trainer: hundreds (objectdetection/equipment_data/instrument_lab). analog_multimeter about 35, digital_clamp_meter about 35, soldering_station about 30, lcr_meter about 15, insulation_tester about 15 source images (more only through augmentation). These five need own photos (50-100 each).
- Dropped TARDATA classes: AC-Voltage&DC-Regulator_Power_Supply, Soldering_iron, Digital_Power_Meter, Current_Probe, cables, hand tools, transformers. Mapping is in `classes.yaml`.
