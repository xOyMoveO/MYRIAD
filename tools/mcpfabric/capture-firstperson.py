#!/usr/bin/env python
"""Build the current MYRIAD jar, start an isolated real Fabric client and capture repeatable first-person views."""
import argparse
import hashlib
import json
import math
import os
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import URLError
from urllib.request import Request, urlopen

TOOLS = Path(__file__).resolve().parent
ROOT = TOOLS.parents[1]
SUPPORT = TOOLS / "firstperson"
REFERENCES = ROOT / "docs/verification/assets/hero-motion"
WORLD = "keheng-grip-test"
PILOT = "MyriadPilot"
BASELINE_SKIN = "minecraft:textures/entity/player/wide/kai.png"
ROLES = {
    "yingxiao": ("夜影", "yingci"), "tianxing": ("天刑", "leitingjian"),
    "fenjue": ("焚诀", "lieyanjian"), "xuanchen": ("墨尘", "zhenqi"),
    "sulan": ("素岚", "fengren"), "lingyuan": ("凌渊", "lingyuan_sword"),
    "hanze": ("寒泽", "shuangbing"), "qingluo": ("青萝", "qingluo_staff"),
    "chixiao": ("赤霄", "chixiao_blade"), "shuo": ("居合 · 朔（收鞘）", "shuo_sword"),
}
LABELS = {role: spec[0] for role, spec in ROLES.items()} | {"shuo-drawn": "居合 · 朔（出鞘）"}


def save_json(file, value):
    file.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def digest(file):
    with file.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def run(args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, **kwargs)


def wait_for(description, predicate, timeout=20):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        result = predicate()
        if result:
            return result
        time.sleep(.15)
    raise RuntimeError(f"Timed out waiting for {description}")


def prepare(run_dir, bridge):
    # Build from the working tree and run the resulting copied JAR. Later compiles cannot
    # change a class or resource that this client has not loaded yet.
    run([sys.executable, TOOLS / "prepare-assets.py"])
    run(["bash", TOOLS / "build-fabric-adapter.sh", bridge])
    launch_file = run_dir / "launch.json"
    run(["bash", "./gradlew", "--offline", "--console=plain", "-x", "downloadAssets",
         "-I", SUPPORT / "launch.gradle", f"-PmyriadCaptureLaunch={launch_file}", "jar", "runClient"])
    launch = json.loads(launch_file.read_text())
    mods = run_dir / "mods"
    mods.mkdir()
    jar = Path(shutil.copy2(launch["jar"], mods))
    shutil.copy2(bridge, mods)
    shutil.copy2(ROOT / "build/mcpfabric-adapter/myriad-acceptance-fabric26.3.jar", mods)

    # The tracked 26.3 header and flat generator come from the synthetic-only arena fixture.
    # The production mod generates its training dimension on entry. No player/chunk data is copied.
    save = run_dir / "saves" / WORLD
    shutil.copytree(SUPPORT / "seed", save)
    shutil.copytree(ROOT / "src/gametest/resources/arena-fixture", save, dirs_exist_ok=True)
    (run_dir / "options.txt").write_text(
        "lang:zh_cn\nonboardAccessibility:false\nfov:0.0\nmainHand:\"right\"\nguiScale:2\n"
        "fullscreen:false\nrenderDistance:4\nsimulationDistance:5\nchatVisibility:2\n"
        "maxFps:40\npauseOnLostFocus:false\ntutorialStep:none\ngamma:0.5\n", encoding="utf-8")

    config = next(arg.split("=", 1)[1] for arg in launch["jvm"] if arg.startswith("-Dfabric.dli.config="))
    isolated_config = run_dir / "launch.cfg"
    isolated_config.write_text("\n".join(line for line in Path(config).read_text().splitlines()
                                         if "fabric.classPathGroups=" not in line) + "\n")
    jvm = [f"-Dfabric.dli.config={isolated_config}" if arg.startswith("-Dfabric.dli.config=") else arg
           for arg in launch["jvm"]]
    # Java argument files also work when dependency paths exceed the Windows command-line limit.
    cp_file = run_dir / "classpath.args"
    cp_file.write_text('-classpath\n"' + os.pathsep.join(launch["cp"]).replace("\\", "/").replace('"', '\\"') + '"\n')
    command = [launch["java"], "-Xmx1500m", *jvm, f"@{cp_file}", launch["main"],
               "--username", PILOT, "--width", "854", "--height", "480"]
    return command, jar


class Client:
    def __init__(self, run_dir, output, manifest):
        self.run_dir, self.output, self.manifest = run_dir, output, manifest
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            self.port = sock.getsockname()[1]
        self.token = secrets.token_hex(32)
        cfg = run_dir / "config" / "mcpfabric.config.json"
        cfg.parent.mkdir()
        save_json(cfg, dict(host="127.0.0.1", port=self.port, requireAuth=True, token=self.token,
                            readToken=secrets.token_hex(32), worldIdKey=secrets.token_hex(32),
                            writeDiscoveryFile=False, auditLog=True, enableWorldWrite=False,
                            enableCommands=True, enablePlayerControl=True, enableVision=True,
                            enableUiControl=True, enableSupervisedControl=False, requireApproval=[]))
        cfg.chmod(0o600)
        self.process = None

    def rpc(self, method, **params):
        if self.process.poll() is not None:
            raise RuntimeError(f"Client exited ({self.process.returncode}). See {self.output / 'client.log'}")
        request = Request(f"http://127.0.0.1:{self.port}/rpc", data=json.dumps(
            {"method": method, "params": params}).encode(), headers={"Authorization": "Bearer " + self.token,
                                                                   "Content-Type": "application/json"})
        with urlopen(request, timeout=10) as response:
            answer = json.load(response)
        if not answer["ok"]:
            raise RuntimeError(f"{method}: {answer.get('error')}")
        return answer["result"]

    def active(self):
        if self.rpc("info.status")["aiPaused"]:
            raise RuntimeError("The player paused test control")

    def state(self):
        return self.rpc("myriad.state")

    def action(self, method, **params):
        self.active()
        return self.rpc(method, **params)

    def command(self, command):
        # Some vanilla commands return zero when the requested value is already set.
        # Scene/role changes are verified against live state, not their numeric return.
        return self.action("command.run", command=f"execute as {PILOT} at @s run {command}")

    def enter(self):
        def ready():
            try:
                status = self.rpc("info.status")
            except (ConnectionError, URLError):
                return False
            if status.get("aiPaused"):
                raise RuntimeError("The player paused test control")
            return "myriad.state" in status["methods"] and self.rpc("screen.get").get("canJoin")
        wait_for("test bridge and title screen", ready, 180)
        self.rpc("info.capabilities")
        self.state()
        self.action("session.join", worldId="sp:" + WORLD)
        wait_for(PILOT, lambda: self.state().get("name") == PILOT, 120)
        self.command("myriad mod on")
        wait_for("mod enabled", lambda: self.state()["modEnabled"])
        self.command("myriad train enter")
        wait_for("training dimension", lambda: self.state()["training"])
        # The role sync may arrive before the new dimension finishes loading its first chunks.
        def world_visible():
            screen = self.rpc("screen.get")
            return screen.get("inWorld") and screen.get("screen") not in ("loading", "connecting")
        wait_for("training scene to finish loading", world_visible, 90)
        if self.state()["screen"] != "none":
            self.action("screen.close")
        self.command("tp @s 0.5 65 0.5 0 0")
        self.command("gamerule minecraft:advance_time false")
        self.command("weather clear")
        self.set_light(False)

    def set_light(self, dark):
        self.command("time set " + ("midnight" if dark else "noon"))
        clock = 18000 if dark else 6000
        wait_for("fixed daylight clock", lambda: self.state()["overworldClock"] % 24000 == clock)
        time.sleep(1.3)

    def validate(self, frame, role, dark=False, drawn=False):
        expected = {"name": PILOT, "character": role, "dimension": "myriad:training_ground", "training": True,
                    "modEnabled": True, "screen": "none", "camera": "FIRST_PERSON", "fov": 70,
                    "mainArm": "RIGHT", "mainItem": "myriad:" + ROLES[role][1], "offhandEmpty": True,
                    "skin": BASELINE_SKIN}
        if role == "shuo":
            expected["shuoDrawn"] = drawn
        for key, val in expected.items():
            if frame.get(key) != val:
                raise RuntimeError(f"{role}: expected {key}={val}, got {frame.get(key)}")
        for key, val in zip(("x", "y", "z", "yaw", "pitch"), (.5, 65, .5, 0, 0)):
            if not math.isclose(frame[key], val, abs_tol=.002):
                raise RuntimeError(f"{role}: wrong camera {key}={frame[key]} (expected {val})")
        if frame["overworldClock"] % 24000 != (18000 if dark else 6000) or frame["rain"] != 0:
            raise RuntimeError(f"{role}: daylight or weather differs from the requested scene")

    def align_camera(self):
        target = {"x": .5, "y": 65, "z": .5, "yaw": 0, "pitch": 0}

        def aligned():
            state = self.state()
            return all(math.isclose(state[key], val, abs_tol=.002) for key, val in target.items())

        if not aligned():
            self.command("tp @s 0.5 65 0.5 0 0")
            wait_for("fixed training camera", aligned)

    def select(self, role):
        self.command("myriad char " + role)
        wait_for(role, lambda: self.state()["character"] == role)
        if self.state()["screen"] != "none":
            self.action("screen.close")
        # Allow the hotbar/equip animation, transient HUD notices and client lighting to settle.
        time.sleep(3.3)
        self.align_camera()
        self.validate(self.state(), role)

    def capture(self, role, label, *, dark=False, drawn=False, motion_button=None):
        self.active()
        self.align_camera()
        self.validate(self.state(), role, dark, drawn)
        motion = motion_button is not None
        count = 26 if motion else 1
        capture_id = self.rpc("myriad.capture", captureFrames=count, intervalTicks=1)["captureId"]
        if motion:
            time.sleep(.17)
            self.action("myriad.input", kind="mouse", button=motion_button)
        def done():
            self.active()
            result = self.rpc("myriad.captureStatus", captureId=capture_id)
            return result if result["complete"] else None
        result = wait_for(label, done, 35)
        if result.get("error") or len(result["frames"]) != count:
            raise RuntimeError(f"{label}: incomplete capture, {result.get('error')}")
        folder = self.output / label
        folder.mkdir()
        records = []
        for index, frame in enumerate(result["frames"]):
            # Shuo may draw during a native attack clip; both states are recorded.
            self.validate(frame, role, dark, frame["shuoDrawn"] if motion else drawn)
            original = Path(frame["path"]).resolve()
            if not original.is_relative_to(self.run_dir / "screenshots" / "myriad" / capture_id):
                raise RuntimeError(f"Unexpected capture file: {original}")
            saved = Path(shutil.copy2(original, folder / f"frame-{index:03}.png"))
            records.append(frame | {"path": str(saved.relative_to(self.output))})
        save_json(folder / "frames.json", records)
        self.manifest["captures"][label] = str((folder / "frames.json").relative_to(self.output))
        save_json(self.output / "manifest.json", self.manifest)
        print(f"Captured {label}: {count} frame(s)", flush=True)

    def dummy(self, create):
        self.action("chat.send", message="/myriad jiaren " + ("create" if create else "remove"))
        time.sleep(.7 if create else .3)

    def collect(self, roles, motion):
        for role in roles:
            self.select(role)
            self.capture(role, role)
            if role == "yingxiao":
                self.set_light(True)
                self.capture(role, "yingxiao-dark", dark=True)
                self.set_light(False)
            mage = role in ("xuanchen", "hanze", "qingluo")
            if role == "shuo" or (motion and not mage):
                self.dummy(True)
            if motion:
                self.capture(role, role + "-motion", motion_button=int(mage))
            elif role == "shuo":
                self.action("myriad.input", kind="mouse", button=0)
            if role == "shuo":
                wait_for("confirmed draw after hitting practice target", lambda: self.state()["shuoDrawn"])
            if role == "shuo" or (motion and not mage):
                self.dummy(False)
            if role == "shuo":
                time.sleep(3.3)
                self.capture(role, "shuo-drawn", drawn=True)


def run_frames(directory, manifest, labels):
    def local_file(relative):
        file = (directory / relative).resolve()
        if not file.is_relative_to(directory) or not file.is_file():
            raise ValueError(f"Capture file is unavailable within {directory}: {relative}")
        return file

    frames = {}
    for label in labels:
        entry = manifest["captures"].get(label)
        if not entry:
            raise ValueError(f"{directory}: missing previous capture '{label}'")
        records = json.loads(local_file(entry).read_text())
        role = label.split("-", 1)[0]
        if len(records) != 1 or records[0].get("character") != role:
            raise ValueError(f"{directory}: '{label}' must be one static frame of {role}")
        frame = records[0]
        if role == "shuo" and frame.get("shuoDrawn") != (label == "shuo-drawn"):
            raise ValueError(f"{directory}: wrong sword state in '{label}'")
        frames[label] = frame | {"file": local_file(frame["path"])}
    return frames


def previous_comparisons(java, output, manifest, previous):
    current = run_frames(output, manifest, previous.keys())
    command = [java, "-Djava.awt.headless=true", SUPPORT / "Comparison.java", "--runs"]
    pairs = {}
    for label, old in previous.items():
        new = current[label]
        for key in ("dimension", "x", "y", "z", "yaw", "pitch", "camera", "fov", "mainArm",
                    "mainItem", "offhandEmpty", "skin", "rain", "guiWidth", "guiHeight"):
            if key not in old or old[key] != new[key]:
                raise ValueError(f"Previous and current '{label}' differ in {key}: {old.get(key)} / {new[key]}")
        if old["overworldClock"] % 24000 != new["overworldClock"] % 24000:
            raise ValueError(f"Previous and current '{label}' have different lighting times")
        title = LABELS.get(label, "夜影（午夜）")
        pair = output / label / "previous-comparison.jpg"
        command.extend([title, old["file"], new["file"], pair])
        pairs[label] = str(pair.relative_to(output))
    run(command)
    return pairs


def comparisons(java, output, roles):
    labels = list(roles)
    if "shuo" in labels:
        labels.insert(labels.index("shuo") + 1, "shuo-drawn")
    command = [java, "-Djava.awt.headless=true", SUPPORT / "Comparison.java", REFERENCES]
    for label in labels:
        command.extend([str(list(LABELS).index(label)), LABELS[label],
                        output / label / "frame-000.png", output / label / "comparison.jpg"])
    run(command)


def encode_motion(output, roles):
    """Use each observed client tick as the timeline, including GPU readback gaps."""
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        print("Motion PNGs and frame timestamps saved. Install ffmpeg for MP4 export.", flush=True)
        return {}
    videos = {}
    for role in roles:
        folder = output / (role + "-motion")
        frames = json.loads((folder / "frames.json").read_text())
        command = [ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-f", "image2pipe", "-framerate", "20",
                   "-vcodec", "png", "-i", "pipe:0", "-c:v", "libx264", "-preset", "veryfast",
                   "-crf", "19", "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(folder / "motion.mp4")]
        with subprocess.Popen(command, stdin=subprocess.PIPE) as encoder:
            try:
                for index, frame in enumerate(frames):
                    repeats = frames[index + 1]["clientTick"] - frame["clientTick"] if index + 1 < len(frames) else 1
                    if repeats < 1:
                        raise RuntimeError(f"{role}: invalid frame tick order")
                    pixels = (output / frame["path"]).read_bytes()
                    for _ in range(repeats):
                        encoder.stdin.write(pixels)
            finally:
                encoder.stdin.close()
            if encoder.wait() != 0:
                raise RuntimeError(f"{role}: ffmpeg could not encode the motion MP4")
        videos[role] = str((folder / "motion.mp4").relative_to(output))
    return videos


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--roles", default=",".join(ROLES), help="comma-separated role ids (default: all ten)")
    parser.add_argument("--output", type=Path, default=ROOT / "outputs/firstperson", help="parent of timestamped runs")
    parser.add_argument("--reference-run", type=Path, help="also compare with static frames from this completed run")
    parser.add_argument("--bridge-jar", type=Path, help="verified MCPFabric 0.10.0+26.3 test mod")
    parser.add_argument("--motion", action="store_true", help="also capture a native attack and recovery sequence per role")
    args = parser.parse_args()
    roles = [item.strip() for item in args.roles.split(",")]
    if len(roles) != len(set(roles)) or any(role not in ROLES for role in roles):
        parser.error("choose unique role ids from " + ", ".join(ROLES))
    version = json.loads((TOOLS / "installation.json").read_text())
    bridge = args.bridge_jar or ROOT / "run-fabric-client/mods" / f"mcpfabric-{version['mod_version']}.jar"
    if not bridge.is_file():
        parser.error(f"verified test mod not found at {bridge}; use --bridge-jar (see tools/mcpfabric/installation.json)")
    previous = None
    if args.reference_run:
        directory = args.reference_run.resolve()
        try:
            reference_manifest = directory / "manifest.json"
            previous_manifest = json.loads(reference_manifest.read_text())
            if previous_manifest.get("status") != "complete" or previous_manifest.get("minecraft") != version["minecraft"]:
                raise ValueError("reference run must be complete and use the same Minecraft version")
            labels = list(roles)
            if "shuo" in roles:
                labels.append("shuo-drawn")
            if "yingxiao" in roles:
                labels.append("yingxiao-dark")
            previous = run_frames(directory, previous_manifest, labels)
        except (OSError, ValueError, TypeError, KeyError) as error:
            parser.error(f"--reference-run: {error}")
    output = args.output.resolve() / datetime.now().astimezone().strftime("%Y%m%d-%H%M%S-%f")
    output.mkdir(parents=True)
    print(f"Evidence: {output}", flush=True)
    manifest = dict(status="running", started=datetime.now(timezone.utc).isoformat(), roles=roles, motion=args.motion,
                    gitHead=run(["git", "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip(),
                    minecraft=version["minecraft"], baseline="#68 / 3908a33", captures={})
    if previous is not None:
        manifest["referenceRun"] = dict(directory=str(directory), manifestSha256=digest(reference_manifest),
                                        jarSha256=previous_manifest["jarSha256"])
    save_json(output / "manifest.json", manifest)
    try:
        with tempfile.TemporaryDirectory(prefix="myriad-fp-") as temp:
            run_dir = Path(temp).resolve()
            command, jar = prepare(run_dir, bridge.resolve())
            manifest["jarSha256"] = digest(jar)
            manifest["baselineSha256"] = {name: digest(REFERENCES / name) for name in
                                         ("closer-grip-1.jpg", "closer-grip-2.jpg")}
            client = Client(run_dir, output, manifest)
            with (output / "client.log").open("w") as log:
                client.process = subprocess.Popen(command, cwd=run_dir, stdout=log, stderr=subprocess.STDOUT)
                try:
                    client.enter()
                    client.collect(roles, args.motion)
                    released = client.rpc("myriad.release")
                    if released["heldInputs"] != 0:
                        raise RuntimeError("Test inputs remained held")
                finally:
                    # This is the directly spawned JVM; the world and options are disposable copies.
                    if client.process.poll() is None:
                        try:
                            client.rpc("myriad.release")
                        except (OSError, RuntimeError):
                            pass  # Bootstrap may have failed before the adapter registered.
                        client.process.terminate()
                        try:
                            client.process.wait(timeout=25)
                        except subprocess.TimeoutExpired:
                            client.process.kill()
                            client.process.wait()
            comparisons(command[0], output, roles)
            if previous is not None:
                manifest["referenceRun"]["pairs"] = previous_comparisons(command[0], output, manifest, previous)
            if args.motion:
                manifest["videos"] = encode_motion(output, roles)
        manifest["status"] = "complete"
    except BaseException as exc:
        manifest.update(status="incomplete", error=str(exc) or type(exc).__name__)
        raise
    finally:
        manifest["finished"] = datetime.now(timezone.utc).isoformat()
        save_json(output / "manifest.json", manifest)
    print(f"Finished: {output}", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError, TimeoutError) as error:
        sys.exit(str(error))
