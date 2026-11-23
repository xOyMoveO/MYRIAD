"""Build editable Blockbench sources and their matching in-game cuboids."""
import argparse
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "art_source/mochen-vfx"
RES = ROOT / "src/main/resources/assets/myriad/textures/particle/vfx"
JAVA = ROOT / "src/main/java/com/myriad/character/skills/xuanchen/rework/InkModelParts.java"
NAMES = ("paper", "inkwood", "brass", "jade")
FACES = ("north", "south", "east", "west", "up", "down")
COLORS = ("#edd6ae", "#343035", "#ab7641", "#467f74")


def part(name, pos, size, material):
    i = NAMES.index(material)
    return dict(id=name, pos=pos, size=size, uv_mode="per_face",
                faces={face: {"uv": [128*i+1, 1, 128*i+127, 127]} for face in FACES})


def dart():
    boxes = [part("ink_spine", (-1, -.5, -9), (2, 1.5, 17), "inkwood")]
    for i, (w, z, depth) in enumerate(((2, 7, 4), (4, 4, 3), (7, 1, 3), (11, -2, 3), (8, -5, 3), (4, -8, 3))):
        boxes += [part(f"paper_step_{i}", (-w/2, -1, z), (w, 1, depth), "paper")]
    boxes += [part("binding_front", (-1.5, -.25, 3), (3, 1.75, 1), "brass"),
              part("binding_rear", (-2, -.25, -4), (4, 1.75, 1.5), "brass"),
              part("raised_seal", (-.5, 1, -.5), (1, .5, 2.5), "jade")]
    return boxes


def slip():
    return [part("paper_back", (-4, -5.5, -1), (8, 10, 1), "paper"),
            part("second_leaf", (-3.5, -5, 0), (7.5, 10, .5), "paper"),
            part("folded_face", (-3.5, -4.5, .5), (7, 9, .5), "paper"),
            part("fold_step", (-3, 3.5, 1), (6, 1.5, 1), "paper"),
            part("oxblood_stamp_back", (-2, -2, 1), (4, 4, .5), "inkwood"),
            part("stamp_inlay", (-1.5, -1.5, 1.5), (3, 3, .5), "jade"),
            part("copper_clasp", (-1, 4, -.5), (2, 2, 3), "brass")]


def gate():
    boxes = [part("lintel", (-17, 33, -3), (34, 4, 6), "inkwood"),
             part("roof_cap", (-20, 37, -3.5), (40, 2, 7), "inkwood"),
             part("frieze", (-12, 34, -3.5), (24, 1, .5), "jade"),
             part("crest", (-3, 33, -4.5), (6, 7, 1.5), "brass"),
             part("crest_inlay", (-1.5, 35, -5), (3, 3, .5), "jade")]
    for s in (-1, 1):
        x = s*14
        # Mirror the structural parts; tags face opposite approaches.
        for name, y, w, h, depth, material in (
                ("foot", 0, 10, 2, 9, "inkwood"), ("pedestal", 2, 8, 2, 7, "brass"),
                ("post", 4, 5, 29, 5, "inkwood"), ("collar_low", 5, 6, 1.5, 6, "brass"),
                ("collar_high", 28, 6, 2, 6, "brass"), ("capital", 30, 8, 3, 7, "inkwood"),
                ("eave_tip", 38, 5, 2, 7, "brass")):
            xx = x + (3*s if name == "eave_tip" else 0)
            boxes.append(part(f"{name}_{s+1}", (xx-w/2, y, -depth/2), (w, h, depth), material))
        boxes += [part(f"front_talisman_{s+1}", (x-1.5, 17+s*2, -3.5), (3, 8, 1), "paper"),
                  part(f"back_talisman_{s+1}", (x-1.5, 16-s*2, 2.5), (3, 8, 1), "paper"),
                  part(f"front_clasp_{s+1}", (x-.75, 25+s*2, -3.5), (1.5, 2, 1), "brass"),
                  part(f"back_clasp_{s+1}", (x-.75, 24-s*2, 2.5), (1.5, 2, 1), "brass")]
    return boxes


def spec(name, parts):
    regions = [dict(id=k, rect=[i*128, 0, 128, 128], fill=color, noise=0)
               for i, (k, color) in enumerate(zip(NAMES, COLORS))]
    # Large material strokes read from a distance, not procedural speckle.
    bands = (
        [(5, 4, 117, 6, "#fff1cd"), (9, 106, 111, 9, "#c09b6f"),
         (29, 34, 68, 7, "#645045"), (32, 45, 8, 33, "#645045"),
         (84, 42, 7, 32, "#645045"), (38, 73, 53, 7, "#645045"),
         (65, 53, 9, 22, "#645045"), (89, 89, 13, 11, "#994b36")],
        [(12, 0, 8, 128, "#504349"), (31, 0, 4, 128, "#453a3d"),
         (87, 0, 8, 128, "#292630"), (107, 0, 14, 128, "#23212b")],
        [(0, 5, 128, 14, "#e3b36b"), (9, 25, 110, 4, "#c89554"),
         (0, 94, 128, 18, "#835431"), (0, 112, 128, 13, "#63422f")],
        [(13, 8, 102, 10, "#94c5a2"), (13, 28, 7, 79, "#699e8b"),
         (93, 23, 15, 82, "#34594f"), (0, 113, 128, 12, "#28413d")],
    )
    for i, group in enumerate(bands):
        for j, (x, y, w, h, hex_color) in enumerate(group):
            regions.append(dict(id=f"{NAMES[i]}_stroke_{j}", rect=[i*128+x,y,w,h], fill=hex_color))
    return dict(meta={"name": name, "schema_version": 1}, target="entity", parts=parts,
                atlas={"width": 512, "height": 128, "regions": regions},
                face_details=[dict(id="silhouette", part=parts[0]["id"], kind="geometry")])


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--generator", type=Path, required=True)
    cli.add_argument("--preview", type=Path, required=True)
    args = cli.parse_args()
    ART.mkdir(parents=True, exist_ok=True)
    args.preview.mkdir(parents=True, exist_ok=True)
    java_data = []
    for name, parts in (("dart", dart()), ("slip", slip()), ("gate", gate())):
        spec_path = ART / f"{name}.spec.json"
        model_path = ART / f"{name}.bbmodel"
        spec_path.write_text(json.dumps(spec(name, parts), indent=2)+"\n", encoding="utf-8")
        cmd = [sys.executable, str(args.generator), str(spec_path), "-o", str(model_path)]
        if name == "gate":
            cmd += ["--png", str(RES / "ink_hd_head_model.png")]
        subprocess.run(cmd, check=True)
        model = json.loads(model_path.read_text(encoding="utf-8"))
        rows = []
        for el in model["elements"]:
            uvs = {tuple(f["uv"]) for f in el["faces"].values()}
            assert len(uvs) == 1 and len(el["faces"]) == 6 and not any(el.get("rotation", []))
            mat = int(next(iter(uvs))[0] // 128)
            rows.append("        {" + ", ".join(f"{n:g}" for n in [*el["from"], *el["to"], mat]) + "}")
        java_data.append(f"    static final double[][] {name.upper()} = {{\n" + ",\n".join(rows) + "\n    };")
        subprocess.run([sys.executable, str(ROOT/"tools/assets/render_bbmodel.py"),
                        str(model_path), str(args.preview/f"{name}.png")], check=True)
    JAVA.write_text("package com.myriad.character.skills.xuanchen.rework;\n\n"
                    "// Generated from validated Blockbench models by tools/vfx/build_mochen_models.py.\n"
                    "final class InkModelParts {\n    private InkModelParts() {}\n"
                    + "\n".join(java_data) + "\n}\n", encoding="utf-8")


if __name__ == "__main__":
    main()
