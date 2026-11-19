#!/usr/bin/env python
"""Compile Cherry Rift's Blockbench rigs into vanilla item parts and clip resources."""

import argparse
import base64
import json
import math
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
SOURCES = REPO / "art_source/cherry-rift-frontline"
ASSETS = REPO / "src/main/resources/assets/myriad"
TIMINGS = REPO / "src/main/resources/data/myriad/cherry_rift/attack_timings.json"
MODEL_IDS = ("frost_hare", "dusk_fox", "jade_beast", "scout_bot", "heavy_walker", "rift_crab")
CHANNELS = ("rotation", "position", "scale")
ITEM_AXES = (-1, 1, -1)


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(document):
    return json.dumps(document, indent=2, ensure_ascii=False, allow_nan=False) + "\n"


def finite(values, size):
    values = [float(component) for component in values]
    if len(values) != size or not all(math.isfinite(component) for component in values):
        raise ValueError(f"invalid {size}-component vector: {values}")
    return values


def source_bounds(element, inflate):
    start = finite(element["from"], 3)
    end = finite(element["to"], 3)
    return ([lo - inflate for lo in start], [hi + inflate for hi in end])


def item_coordinates(source, elements):
    """Recover the uniform scale and flipped axes from the existing static item."""
    if len(source) != len(elements) or not source:
        raise ValueError("source and static item differ in cube count")
    reference, item = source[0], elements[0]
    low, high = source_bounds(reference, float(reference.get("inflate", 0)))
    scale = (item["to"][0] - item["from"][0]) / (high[0] - low[0])
    signed = [axis * scale for axis in ITEM_AXES]
    offset = [
        item["from"][i] - signed[i] * (low[i] if axis > 0 else high[i])
        for i, axis in enumerate(ITEM_AXES)
    ]

    def convert(point):
        return [axis * value + origin for axis, value, origin in zip(signed, point, offset)]

    for cube, part in zip(source, elements):
        low, high = source_bounds(cube, float(cube.get("inflate", 0)))
        ranges = list(zip(convert(low), convert(high)))
        expected = ([min(axis) for axis in ranges], [max(axis) for axis in ranges])
        if any(abs(got - want) > 1e-5 for vector, key in zip(expected, ("from", "to"))
               for got, want in zip(vector, part[key])):
            raise ValueError(f"{cube['name']}: source cube no longer matches the existing static item")
    return signed, convert


def seconds_to_ticks(value):
    ticks = float(value) * 20
    if not math.isfinite(ticks):
        raise ValueError("non-finite animation time")
    return round(ticks, 6)


def key_time_for(clip, name):
    ticks = seconds_to_ticks(clip["length"])
    if ticks <= 0 or not ticks.is_integer():
        raise ValueError(f"{name}: duration must be a positive whole number of game ticks")
    if clip["loop"] != ("loop" if name == "move" else "once"):
        raise ValueError(f"{name}: unexpected loop mode {clip['loop']}")
    return int(ticks)


def hit_tick(clip, duration):
    frames = clip["animators"].get("effects", {}).get("keyframes", [])
    hits = [seconds_to_ticks(frame["time"]) for frame in frames
            if frame["channel"] == "timeline"
            and any(point.get("script") == "hit" for point in frame["data_points"])]
    if len(hits) != 1 or not hits[0].is_integer() or not 0 < hits[0] < duration:
        raise ValueError("attack must have one interior whole-tick hit marker")
    return int(hits[0])


def collect_bones(outliner, source, animated):
    indices = {element["uuid"]: index for index, element in enumerate(source)}
    if len(indices) != len(source):
        raise ValueError("duplicate cube UUID")
    bones = [{"name": "rest", "parent": -1, "origin": [0, 0, 0], "cubes": []}]
    bone_indices = {}
    used = set()

    def visit(nodes, parent):
        for node in nodes:
            if isinstance(node, str):
                cube = indices[node]
                if cube in used:
                    raise ValueError(f"cube appears twice in outliner: {source[cube]['name']}")
                used.add(cube)
                bones[parent]["cubes"].append(cube)
                continue
            if any(finite(node.get("rotation", [0, 0, 0]), 3)):
                raise ValueError(f"{node['name']}: bake resting group rotation into the source cubes first")
            group = node["uuid"]
            nearest = parent
            if group in animated:
                nearest = len(bones)
                bone_indices[group] = nearest
                bones.append({"name": node["name"], "parent": parent,
                              "origin": finite(node["origin"], 3), "cubes": []})
            visit(node["children"], nearest)

    visit(outliner, 0)
    if len(used) != len(source) or bone_indices.keys() != animated:
        raise ValueError("outliner must contain every cube and all animated groups")
    return bones, bone_indices


def animation_tracks(clip, duration, bone_indices, bone_count, signed):
    tracks = [{} for _ in range(bone_count)]
    for uuid, animator in clip["animators"].items():
        if uuid == "effects":
            continue
        if animator.get("type", "bone") != "bone" or animator.get("rotation_global", False):
            raise ValueError("only local bone tracks are supported")
        track = tracks[bone_indices[uuid]]
        for key in animator.get("keyframes", []):
            channel = key["channel"]
            points = key["data_points"]
            if channel not in CHANNELS or key.get("interpolation", "linear") != "linear" or len(points) != 1:
                raise ValueError(f"{animator.get('name', uuid)}: expected a linear XYZ pose key")
            point = points[0]
            values = finite([point[axis] for axis in "xyz"], 3)
            time = seconds_to_ticks(key["time"])
            if not 0 <= time <= duration:
                raise ValueError("pose key outside clip duration")
            if channel == "position":
                values = [value * axis / 16 for value, axis in zip(values, signed)]
            elif channel == "rotation":
                values = [value * axis for value, axis in zip(values, ITEM_AXES)]
            elif any(value <= 0 for value in values):
                raise ValueError("scale must be positive")
            track.setdefault(channel, []).append({"tick": time, "value": values})
        for channel, keys in track.items():
            keys.sort(key=lambda key: key["tick"])
            if len({key["tick"] for key in keys}) != len(keys):
                raise ValueError(f"{channel} contains duplicate key times")
            if keys[0]["tick"] != 0 or keys[-1]["tick"] != duration:
                raise ValueError(f"{animator.get('name', uuid)}/{channel} requires explicit start and end poses")
            neutral = [1, 1, 1] if channel == "scale" else [0, 0, 0]
            closing_pose = keys[0]["value"] if clip["name"] == "move" else neutral
            for end, reference in zip(keys[-1]["value"], closing_pose):
                difference = end - reference
                if channel == "rotation":
                    difference = math.remainder(difference, 360)
                if abs(difference) > 1e-5:
                    raise ValueError(f"{animator.get('name', uuid)}/{channel} must close the clip")
    return tracks


def compile_model(model_id):
    original = read_json(SOURCES / f"{model_id}.bbmodel")
    model = read_json(ASSETS / "models/item/cherry_rift" / f"{model_id}.json")
    embedded_atlas = original["textures"][0]["source"].removeprefix("data:image/png;base64,")
    atlas = ASSETS / f"textures/item/cherry_rift/{model_id}.png"
    if base64.b64decode(embedded_atlas, validate=True) != atlas.read_bytes():
        raise ValueError("Blockbench atlas differs from the in-game atlas")
    signed, convert = item_coordinates(original["elements"], model["elements"])
    clips = original.get("animations", [])
    animations = {clip["name"]: clip for clip in clips}
    if len(clips) != 2 or set(animations) != {"move", "attack"}:
        raise ValueError("expected exactly move and attack clips")
    animated = {uuid for clip in animations.values() for uuid in clip["animators"] if uuid != "effects"}
    bones, bone_indices = collect_bones(original["outliner"], original["elements"], animated)
    pose_parts, files = [], {}
    for index, bone in enumerate(bones):
        pose = {"name": bone["name"], "parent": bone["parent"],
                "pivot": [(value - 8) / 16 for value in convert(bone["origin"])]}
        if bone["cubes"]:
            path = f"cherry_rift_bones/{model_id}/{index}"
            pose["item"] = f"myriad:{path}"
            files[ASSETS / f"items/{path}.json"] = {
                "model": {"type": "minecraft:model", "model": f"myriad:item/{path}"}
            }
            files[ASSETS / f"models/item/{path}.json"] = {
                "textures": model["textures"],
                "elements": [model["elements"][cube] for cube in bone["cubes"]],
            }
        pose_parts.append(pose)
    asset = {"bones": pose_parts}
    for name, clip in animations.items():
        duration = key_time_for(clip, name)
        asset[name] = {"duration": duration,
                       "tracks": animation_tracks(clip, duration, bone_indices, len(bones), signed)}
    files[ASSETS / f"cherry_rift_animation/{model_id}.json"] = asset
    duration = asset["attack"]["duration"]
    return files, {"duration": duration, "hit": hit_tick(animations["attack"], duration)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Compare with the committed resources.")
    options = parser.parse_args()
    generated, timings = {}, {}
    for model_id in MODEL_IDS:
        try:
            files, timing = compile_model(model_id)
        except (KeyError, TypeError, ValueError) as failure:
            parser.error(f"{model_id}: {failure}")
        generated.update(files)
        timings[f"cherry_{model_id}"] = timing
    generated[TIMINGS] = timings
    for path, data in generated.items():
        serialized = write_json(data)
        if options.check:
            if not path.is_file() or path.read_text(encoding="utf-8") != serialized:
                parser.error(f"generated file differs: {path.relative_to(REPO)}")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(serialized, encoding="utf-8")
    print(f"{'Checked' if options.check else 'Generated'} {len(generated)} resources for {len(MODEL_IDS)} creatures.")


if __name__ == "__main__":
    main()
