"""Play the out-of-range JAR through native controls in a fresh local training client."""

import argparse
import importlib.util
import json
import shutil
import subprocess
import tempfile
import time
from pathlib import Path

from capture import HERE, OUT, ROOT, save

spec = importlib.util.spec_from_file_location(
    "fixture_client", ROOT / "tools/mcpfabric/capture-firstperson.py"
)
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


def exercise(c):
    def key(name):
        c.action("myriad.input", kind="key", key=name, holdTicks=1)

    def click(x, y):
        c.action("myriad.input", kind="mouse", button=0, x=int(x), y=int(y), holdTicks=1)
        time.sleep(0.16)

    def screen(name):
        fixture.wait_for(name, lambda: c.state()["screen"] == name, 8)

    c.enter()
    c.command("myriad char keheng")
    fixture.wait_for("Keheng", lambda: c.state()["character"] == "keheng")
    if c.state()["screen"] != "none":
        c.action("screen.close")
    c.command("tp @s 0.5 65 0.5 0 32")
    key("key.myriad.lobby")
    screen("LobbyScreen")
    state = c.state()
    w, h = state["guiWidth"], state["guiHeight"]
    left = (w - min(660, w - 20)) // 2
    top = (h - min(235, h - 12)) // 2
    click(left + 54, top + 32 + 27 + 10)
    screen("TrainScreen")
    pw, ph = min(440, w - 24), min(480, h - 16)
    vx = (w - pw) // 2 + 12
    vy = (h - ph) // 2 + 49
    aw = min(142, (pw - 24) // 2)
    ax = vx + pw - 24 - aw - 7
    bw = (aw - 9) // 4
    for col in (1, 3):
        click(ax + col * (bw + 3) + bw // 2, vy + 36 + 16)
    fixture.wait_for("Lv20", lambda: c.state()["level"] == 20, 6)
    c.action("screen.close")
    screen("none")
    key("key.myriad.info")
    screen("FirstGroupScreen")
    pw = min(640, w - 2 * min(12, w // 12), max(360, round(w * 0.86)))
    ph = min(440, h - 2 * min(6, h // 16))
    px, py = (w - pw) // 2, (h - ph) // 2
    nav = max(52, min(64, pw // 8))
    body_x = px + 14 + nav
    body_y = py + min(26, ph // 6) + 31
    body_w = pw - 28 - nav
    footer = min(31, ph // 6)
    body_h = ph - footer - (body_y - py)
    rh = min(64, max(34, body_h // 4))
    # R-A is the left card of the fourth displayed row. The confirmation stays in the footer.
    click(body_x + int(body_w * 0.47), body_y + 3 * rh + rh // 2)
    for rank in (1, 2, 3):
        click(body_x + body_w - 24, py + ph - footer + 12)
        fixture.wait_for(
            f"Ultimate rank {rank}", lambda: c.state().get("skills", {}).get("keheng_yimiao", 0) >= rank, 6
        )
        time.sleep(1.15)
    c.action("screen.close")
    screen("none")
    before = c.state()
    key("key.myriad.skill_r")
    fixture.wait_for("Point selection", lambda: c.state()["placementType"] != 0, 5)
    aiming = c.state()
    c.action("myriad.input", kind="mouse", button=0, holdTicks=1)
    fixture.wait_for(
        "Server committed R", lambda: c.state()["placementType"] == 0 and c.state()["cooldowns"][3] > 0, 5
    )
    after = c.state()
    released = c.rpc("myriad.release")
    fields = (
        "character",
        "level",
        "skills",
        "mana",
        "noMana",
        "noCooldown",
        "cooldowns",
        "placementType",
        "heldInputs",
        "dimension",
        "worldTick",
        "clientTick",
        "nativeKeyEvents",
        "nativeMouseEvents",
    )

    def pick(state):
        return {k: state[k] for k in fields}

    if released["heldInputs"] or before["mana"] <= after["mana"]:
        raise RuntimeError("The trial did not observe payment and release")
    return dict(
        before=pick(before),
        aiming=pick(aiming),
        after=pick(after),
        heldAtEnd=released["heldInputs"],
        minecraft="26.3",
        world="synthetic training",
        input="native R + left mouse",
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bridge-jar", type=Path, required=True)
    args = parser.parse_args()
    evidence = json.loads((HERE / "samples/engine-evidence.json").read_text())["manual"]
    folder = OUT / "client"
    folder.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="myriad-benchmark-client-") as temp:
        run = Path(temp).resolve()
        command, jar = fixture.prepare(run, args.bridge_jar.resolve())
        shutil.copy2(OUT / "manual-100t" / evidence["jar"], jar)
        if fixture.digest(jar) != evidence["jarSha256"]:
            raise RuntimeError("Training client JAR differs from the manual-value capture")
        c = fixture.Client(run, folder, {})
        with (folder / "client.log").open("w") as log:
            c.process = subprocess.Popen(command, cwd=run, stdout=log, stderr=subprocess.STDOUT)
            try:
                result = exercise(c)
                result["jarSha256"] = evidence["jarSha256"]
                save(HERE / "samples/client-trial.json", result)
            finally:
                if c.process.poll() is None:
                    try:
                        c.rpc("myriad.release")
                    except (OSError, RuntimeError):
                        pass
                    c.process.terminate()
                    try:
                        c.process.wait(timeout=25)
                    except subprocess.TimeoutExpired:
                        c.process.kill()
                        c.process.wait()
    print("Manual-value JAR cast with native R and left mouse; no held inputs.")


if __name__ == "__main__":
    main()
