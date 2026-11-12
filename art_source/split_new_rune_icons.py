from pathlib import Path
from PIL import Image

SOURCE = Path(r"C:\Users\Administrator\.codex\generated_images\01a02d4a-659e-76a0-ae97-dd121a6cf00e\exec-9d4eb0b8-6275-4b95-814f-65c37b89c561.png")
OUTPUT = Path(r"E:\mod\MDK-26.2-ModDevGradle-main\src\main\resources\assets\myriad\textures\gui\sprites")
CONTACT = Path(r"E:\mod\MDK-26.2-ModDevGradle-main\art_source\new_runes_contact.png")
NAMES = [
    "zhuixing_tadou", "shushi_huiliu", "zhenyue_lingke", "chengxi_pozhan",
    "bingfeng_xiangji", "jishi_yuhui", "lingmai_xingzhe", "taichu_huixiang",
    "zhanyin_duanguo", "wanxiang_guixu", "douzhuan_xingyi", "tongxin_tianqi",
    "tianqiong_haoling", "wujian_zhuilie",
]

source = Image.open(SOURCE).convert("RGB")
OUTPUT.mkdir(parents=True, exist_ok=True)
contact = Image.new("RGB", (4 * 128, 4 * 128), (2, 5, 16))
for index, name in enumerate(NAMES):
    row, column = divmod(index, 4)
    left = round(column * source.width / 4) + 3
    top = round(row * source.height / 4) + 3
    right = round((column + 1) * source.width / 4) - 3
    bottom = round((row + 1) * source.height / 4) - 3
    icon = source.crop((left, top, right, bottom)).resize((64, 64), Image.Resampling.LANCZOS)
    icon.save(OUTPUT / f"rune_{name}.png", optimize=True)
    contact.paste(icon.resize((128, 128), Image.Resampling.NEAREST), (column * 128, row * 128))
contact.save(CONTACT, optimize=True)
print(f"created {len(NAMES)} icons and {CONTACT}")
