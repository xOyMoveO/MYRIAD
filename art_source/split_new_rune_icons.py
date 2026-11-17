"""Split a historical rune icon sheet; requires Pillow and explicit source/output paths.

This preserves retired artwork tooling and does not install resources into MYRIAD.
"""
import argparse
from pathlib import Path

NAMES = [
    "zhuixing_tadou", "shushi_huiliu", "zhenyue_lingke", "chengxi_pozhan",
    "bingfeng_xiangji", "jishi_yuhui", "lingmai_xingzhe", "taichu_huixiang",
    "zhanyin_duanguo", "wanxiang_guixu", "douzhuan_xingyi", "tongxin_tianqi",
    "tianqiong_haoling", "wujian_zhuilie",
]



def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path, help="Historical four-by-four source sheet")
    parser.add_argument("--output", required=True, type=Path, help="Explicit output directory; no game resources are selected automatically")
    args = parser.parse_args()
    from PIL import Image

    with Image.open(args.source) as original:
        source = original.convert("RGB")
    args.output.mkdir(parents=True, exist_ok=True)
    contact = Image.new("RGB", (4 * 128, 4 * 128), (2, 5, 16))
    for index, name in enumerate(NAMES):
        row, column = divmod(index, 4)
        left = round(column * source.width / 4) + 3
        top = round(row * source.height / 4) + 3
        right = round((column + 1) * source.width / 4) - 3
        bottom = round((row + 1) * source.height / 4) - 3
        icon = source.crop((left, top, right, bottom)).resize((64, 64), Image.Resampling.LANCZOS)
        icon.save(args.output / f"rune_{name}.png", optimize=True)
        contact.paste(icon.resize((128, 128), Image.Resampling.NEAREST), (column * 128, row * 128))
    contact_path = args.output / "new_runes_contact.png"
    contact.save(contact_path, optimize=True)
    print(f"created {len(NAMES)} historical icons and {contact_path}; not installed")


if __name__ == "__main__":
    main()
