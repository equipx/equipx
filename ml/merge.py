#!/usr/bin/env python3
"""Remap / dedupe / split Roboflow YOLO exports into one 10-class EquipX dataset.

Usage (works locally and in Colab):
  python merge.py --raw datasets/raw --out datasets/equipx --own own_photos [--whole-image-boxes]
Needs only: pyyaml, pillow, numpy.
"""
import argparse, random, re, shutil, sys
from collections import defaultdict
from pathlib import Path

import numpy as np
import yaml
from PIL import Image

IMG_EXT = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def norm(s):
    return re.sub(r"[\s_\-]+", "", str(s)).lower()


def dhash(path, size=8):
    with Image.open(path) as im:
        g = im.convert("L").resize((size + 1, size), Image.BILINEAR)
    a = np.asarray(g, dtype=np.int16)
    bits = (a[:, 1:] > a[:, :-1]).flatten()
    return int("".join("1" if b else "0" for b in bits), 2)


def parse_label(path, cmap):
    """Return [(new_id, cx, cy, w, h)] for mapped classes. cmap: old_id -> new_id. Handles polygons."""
    out = []
    if not path.exists():
        return out
    for line in path.read_text().splitlines():
        p = line.split()
        if len(p) < 5:
            continue
        try:
            old = int(float(p[0]))
            vals = [float(v) for v in p[1:]]
        except ValueError:
            continue
        if old not in cmap:
            continue
        if len(vals) == 4:
            cx, cy, w, h = vals
        else:  # polygon -> bbox
            xs, ys = vals[0::2], vals[1::2]
            x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
            cx, cy, w, h = (x0 + x1) / 2, (y0 + y1) / 2, x1 - x0, y1 - y0
        if w <= 0 or h <= 0:
            continue
        out.append((cmap[old], cx, cy, w, h))
    return out


def collect_public(raw, cfg, ids):
    items = []  # dict(src, boxes, group, origin)
    stats = {}
    for ds_dir in sorted(p for p in raw.iterdir() if p.is_dir()):
        name = ds_dir.name
        dcfg = cfg["datasets"].get(name)
        if not dcfg:
            print(f"[skip] {name}: not in classes.yaml")
            continue
        data = yaml.safe_load((ds_dir / "data.yaml").read_text())
        names = data["names"]
        names = [names[k] for k in sorted(names)] if isinstance(names, dict) else list(names)
        lut = {norm(k): v for k, v in dcfg["mapping"].items()}
        cmap, unmapped = {}, []
        for i, n in enumerate(names):
            tgt = lut.get(norm(n))
            if tgt:
                cmap[i] = ids.index(tgt)
            else:
                unmapped.append(n)
        print(f"[{name}] mapped {len(cmap)}/{len(names)}; dropped classes: {unmapped}")
        n_img = n_kept = 0
        for split in ("train", "valid", "val", "test"):
            idir = ds_dir / split / "images"
            if not idir.exists():
                continue
            for img in idir.iterdir():
                if img.suffix.lower() not in IMG_EXT:
                    continue
                n_img += 1
                boxes = parse_label(ds_dir / split / "labels" / (img.stem + ".txt"), cmap)
                if not boxes:
                    continue
                n_kept += 1
                stem = img.stem.split(".rf.")[0]  # augmented copies share the source stem
                items.append(dict(src=img, boxes=boxes, group=f"{name}:{stem}", origin=name, own=False))
        stats[name] = (n_img, n_kept)
    return items, stats


def collect_own(own, ids, whole):
    items, skipped = [], 0
    if not own.exists():
        return items
    for cdir in sorted(p for p in own.iterdir() if p.is_dir()):
        if cdir.name not in ids:
            print(f"[own] ignoring folder {cdir.name} (not a class id)")
            continue
        cid = ids.index(cdir.name)
        for img in sorted(cdir.iterdir()):
            if img.suffix.lower() not in IMG_EXT:
                continue
            txt = img.with_suffix(".txt")
            if txt.exists():
                boxes = [(cid, *b[1:]) for b in parse_label(txt, {k: k for k in range(len(ids))})]
            elif whole:
                boxes = [(cid, 0.5, 0.5, 0.98, 0.98)]
            else:
                boxes = []
            if not boxes:
                skipped += 1
                continue
            items.append(dict(src=img, boxes=boxes, group=f"own:{cdir.name}/{img.stem}", origin="own", own=True))
    if skipped:
        print(f"[own] {skipped} unlabeled photos skipped (add .txt labels or pass --whole-image-boxes)")
    return items


def dedupe(items, thresh=2):
    kept, hashes, dups = [], [], 0
    for it in items:
        try:
            h = dhash(it["src"])
        except Exception:
            continue
        if any(bin(h ^ o).count("1") <= thresh for o in hashes):
            dups += 1
            continue
        hashes.append(h)
        kept.append(it)
    print(f"[dedupe] removed {dups} near-duplicates, kept {len(kept)}")
    return kept


def split(items, ids, seed, own_test=0.6):
    rng = random.Random(seed)
    counts = defaultdict(int)
    for it in items:
        for b in it["boxes"]:
            counts[b[0]] += 1
    groups = defaultdict(list)
    for it in items:
        groups[it["group"]].append(it)
    by_primary = defaultdict(list)
    for g, its in groups.items():
        cls = {b[0] for it in its for b in it["boxes"]}
        by_primary[min(cls, key=lambda c: counts[c])].append(its)  # rarest class drives stratification
    out = {"train": [], "valid": [], "test": []}
    for cls, gl in by_primary.items():
        rng.shuffle(gl)
        own_g = [g for g in gl if g[0]["own"]]
        pub_g = [g for g in gl if not g[0]["own"]]
        for lst, (a, b) in ((pub_g, (0.8, 0.9)), (own_g, (1 - own_test - 0.2, 1 - own_test))):
            n = len(lst)
            i, j = round(n * a), round(n * b)
            for k, g in enumerate(lst):
                out["train" if k < i else "valid" if k < j else "test"].extend(g)
    return out


def write(out_dir, splits, ids):
    if out_dir.exists():
        shutil.rmtree(out_dir)
    for s, its in splits.items():
        (out_dir / s / "images").mkdir(parents=True)
        (out_dir / s / "labels").mkdir(parents=True)
        for n, it in enumerate(its):
            stem = f"{it['origin']}_{n:05d}"
            shutil.copy(it["src"], out_dir / s / "images" / (stem + it["src"].suffix.lower()))
            lines = [f"{c} {cx:.6f} {cy:.6f} {w:.6f} {h:.6f}" for c, cx, cy, w, h in it["boxes"]]
            (out_dir / s / "labels" / (stem + ".txt")).write_text("\n".join(lines) + "\n")
    (out_dir / "data.yaml").write_text(yaml.safe_dump(
        {"path": str(out_dir.resolve()), "train": "train/images", "val": "valid/images",
         "test": "test/images", "nc": len(ids), "names": {i: n for i, n in enumerate(ids)}}, sort_keys=False))


def report(splits, ids):
    print(f"\n{'class':22}" + "".join(f"{s+' img/box':>16}" for s in splits) + f"{'total img':>11}")
    weak = []
    for ci, name in enumerate(ids):
        row, tot = "", 0
        for s, its in splits.items():
            imgs = [it for it in its if any(b[0] == ci for b in it["boxes"])]
            boxes = sum(1 for it in its for b in it["boxes"] if b[0] == ci)
            row += f"{len(imgs):>9}/{boxes:<6}"
            tot += len(imgs)
        print(f"{name:22}{row}{tot:>11}")
        if tot < 100:
            weak.append((name, tot))
    print(f"{'ALL':22}" + "".join(f"{len(its):>16}" for its in splits.values()))
    for n, t in weak:
        print(f"WARNING: class '{n}' has only {t} images (< 100): add own photos in own_photos/{n}/")
    own_test = {c: 0 for c in ids}
    for it in splits["test"]:
        if it["own"]:
            own_test[ids[it["boxes"][0][0]]] += 1
    if not any(own_test.values()):
        print("NOTE: no own photos in the test split: metrics only reflect public data, not your lab.")


def main():
    here = Path(__file__).parent
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", default=str(here / "datasets/raw"))
    ap.add_argument("--out", default=str(here / "datasets/equipx"))
    ap.add_argument("--own", default=str(here / "own_photos"))
    ap.add_argument("--classes", default=str(here / "classes.yaml"))
    ap.add_argument("--whole-image-boxes", action="store_true",
                    help="unlabeled own photos get one near-full-image box of their folder's class")
    ap.add_argument("--own-test-fraction", type=float, default=0.6)
    ap.add_argument("--seed", type=int, default=0)
    a = ap.parse_args()
    cfg = yaml.safe_load(open(a.classes))
    ids = cfg["classes"]
    raw = Path(a.raw)
    if not raw.exists():
        sys.exit(f"{raw} not found: download datasets first")
    pub, stats = collect_public(raw, cfg, ids)
    own = collect_own(Path(a.own), ids, a.whole_image_boxes)
    print(f"public images with mapped boxes: {len(pub)}; own photos: {len(own)}")
    items = dedupe(pub) + own  # never dedupe away own photos
    splits = split(items, ids, a.seed, a.own_test_fraction)
    write(Path(a.out), splits, ids)
    report(splits, ids)
    print(f"\nwrote {a.out}/data.yaml")


if __name__ == "__main__":
    main()
