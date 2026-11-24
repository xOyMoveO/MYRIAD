"""Normalize reviewed MYRIAD portrait sources and make a full/small contact sheet."""

import argparse
import hashlib
from pathlib import Path
import re
from zipfile import ZipFile

from PIL import Image, ImageCms, ImageDraw, ImageFont
from io import BytesIO

ROOT = Path(__file__).resolve().parents[2]
ROSTER = {
    "yingxiao": "夜影", "tianxing": "天刑", "fenjue": "焚诀", "xuanchen": "墨尘",
    "sulan": "素岚", "lingyuan": "凌渊", "hanze": "寒泽", "qingluo": "青萝",
    "chixiao": "赤霄", "yunsheng": "云笙", "shuo": "朔", "keheng": "刻衡",
    "yike": "伊珂", "rivet": "铆钉",
}
PREFIX = "assets/myriad/textures/gui/sprites/portraits/"
SRGB = ImageCms.ImageCmsProfile(ImageCms.createProfile("sRGB"))


def current_roster():
    source = ROOT / "src/main/java/com/myriad/character/CharacterType.java"
    return set(re.findall(r'^    [A-Z0-9_]+\("([a-z0-9_]+)",\s*Faction\.', source.read_text("utf-8"), re.M))


def normalize(path):
    with Image.open(path) as raw:
        raw.load()
        if raw.format != "PNG" or raw.width != raw.height or raw.width < 256:
            raise ValueError(f"{path.name}: expected a square PNG of at least 256 pixels")
        if "A" in raw.getbands() and raw.getchannel("A").getextrema() != (255, 255):
            raise ValueError(f"{path.name}: portraits must be opaque")
        rgb = raw.convert("RGB")
        icc = raw.info.get("icc_profile")
        if icc:
            rgb = ImageCms.profileToProfile(rgb, ImageCms.ImageCmsProfile(BytesIO(icc)), SRGB, outputMode="RGB")
        return rgb.resize((256, 256), Image.Resampling.LANCZOS)


def verify_set(images):
    if set(images) != current_roster() or set(images) != set(ROSTER):
        raise ValueError(f"Portrait coverage mismatch, expected {sorted(current_roster())}: {sorted(images)}")
    hashes = set()
    for role, png in images.items():
        if png.size != (256, 256) or png.mode != "RGB" or png.format not in ("PNG", None):
            raise ValueError(f"{role}: expected an opaque 256×256 RGB PNG")
        digest = hashlib.sha256(png.tobytes()).digest()
        if digest in hashes:
            raise ValueError(f"{role}: bitmap duplicated by another role")
        hashes.add(digest)


def contact_sheet(images, path, font_path):
    # Preview uses the same approximate head size as the 40px information panel and larger card.
    cols, cell_w, cell_h = 7, 176, 244
    sheet = Image.new("RGB", (cols * cell_w + 24, cell_h * 2 + 72), "#111923")
    draw = ImageDraw.Draw(sheet)
    label_font = ImageFont.truetype(str(font_path), 18) if font_path else ImageFont.load_default(16)
    game_font = ImageFont.load_default(13)
    draw.text((24, 20), "MYRIAD   /   CHARACTER PORTRAITS   /   14 HEROES", font=game_font, fill="#e9d6ae")
    for i, (role, name) in enumerate(ROSTER.items()):
        x, y = 14 + i % cols * cell_w, 52 + i // cols * cell_h
        pic = images[role]
        sheet.paste(pic.resize((160, 160), Image.Resampling.LANCZOS), (x + 4, y + 4))
        sheet.paste(pic.resize((40, 40), Image.Resampling.LANCZOS), (x + 4, y + 175))
        sheet.paste(pic.resize((24, 24), Image.Resampling.LANCZOS), (x + 50, y + 183))
        draw.text((x + 84, y + 177), name if font_path else role, font=label_font, fill="#e7eaf0")
        if font_path:
            draw.text((x + 84, y + 202), role, font=game_font, fill="#9caeb9")
    sheet.save(path, format="PNG")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", metavar="ID=PNG", action="append", default=[])
    parser.add_argument("--stage", type=Path)
    parser.add_argument("--font", type=Path)
    parser.add_argument("--verify-jar", type=Path)
    parser.add_argument("--baseline-jar", type=Path, help="Optional packaged baseline; assert portrait-only changes")
    args = parser.parse_args()
    if args.verify_jar:
        with ZipFile(args.verify_jar) as jar:
            names = [name for name in jar.namelist()
                     if name.startswith(PREFIX) and name.endswith(".png")]
            if len(names) != len(set(names)):
                raise ValueError("Duplicate jar entries")
            images = {}
            for name in names:
                with Image.open(BytesIO(jar.read(name))) as image:
                    image.load()
                    images[name[len(PREFIX):-4]] = image.copy()
            verify_set(images)
            if args.baseline_jar:
                with ZipFile(args.baseline_jar) as baseline:
                    def fingerprint(archive):
                        return {e.filename: (e.file_size, e.CRC) for e in archive.infolist() if not e.is_dir()}

                    before, after = fingerprint(baseline), fingerprint(jar)
                    changed = {name for name in before.keys() | after.keys() if before.get(name) != after.get(name)}
                    if changed != set(names):
                        raise ValueError(f"Image-only update has unexpected archive changes: {sorted(changed ^ set(names))}")
                    print("PASS: 14 portrait changes and no other changed JAR entries")
        print("PASS: 14 unique 256px portraits, exact roster coverage inside the release JAR")
        return
    if not args.stage:
        parser.error("--stage required when normalizing portraits")
    paths = dict(item.split("=", 1) for item in args.image)
    if len(paths) != len(args.image):
        parser.error("Every portrait ID may be supplied only once")
    images = {role: normalize(Path(path)) for role, path in paths.items()}
    verify_set(images)
    args.stage.mkdir(parents=True, exist_ok=True)
    for role, png in images.items():
        png.save(args.stage / f"{role}.png", format="PNG", optimize=True)
    contact_sheet(images, args.stage.parent / "roster-portraits.png", args.font)
    print("PASS: 14 square, opaque, unique sprites plus 40px/24px contact thumbnails")


if __name__ == "__main__":
    main()
