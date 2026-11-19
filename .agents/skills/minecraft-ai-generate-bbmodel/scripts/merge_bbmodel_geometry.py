#!/usr/bin/env python
"""Stage existing-cube geometry edits from two Blockbench project exports."""

import argparse
import copy
import json
from pathlib import Path

from bbmodel_to_spec import recover_parents
from generate_bbmodel import (
    SpecError,
    _snap,
    build_bbmodel,
    load_json,
    resolve_atlas_size,
    validate_bbmodel,
    validate_geometry,
    validate_schema,
)


GEOMETRY_KEYS = {"from", "to", "origin", "rotation", "inflate"}
PROJECT_METADATA = {"ai_used", "ai_agents"}


def coordinates(values):
    return [round(value, 8) for value in values]


def geometry(cube):
    return {
        "from": coordinates(cube["from"]),
        "to": coordinates(cube["to"]),
        "origin": coordinates(cube.get("origin", [0, 0, 0])),
        "rotation": coordinates(cube.get("rotation", [0, 0, 0])),
        "inflate": round(cube.get("inflate", 0), 8),
    }


def cubes_by_name(model):
    cubes = {}
    ids = set()
    for cube in model["elements"]:
        name, uuid = cube["name"], cube["uuid"]
        if cube["type"] != "cube" or name in cubes or uuid in ids:
            raise SpecError("Expected cuboids with unique names and UUIDs")
        cubes[name] = cube
        ids.add(uuid)
    return cubes


def metadata_changes(before, after, ignored):
    return sorted(key for key in before.keys() | after.keys()
                  if key not in ignored and before.get(key) != after.get(key))


def offset_values(values, offsets):
    return [round(value + offset, 8) if offset else value
            for value, offset in zip(values, offsets)]


def update_bounds(part, before, after):
    # Apply the endpoint deltas to the source values to preserve grid snapping
    # and the generator's anti-Z-fighting offset.
    start_delta = [end - start for start, end in zip(before["from"], after["from"])]
    end_delta = [end - start for start, end in zip(before["to"], after["to"])]
    if any(start_delta):
        part["pos"] = offset_values(part["pos"], start_delta)
    if start_delta != end_delta:
        part["size"] = offset_values(part["size"],
                                     (end - start for end, start in zip(end_delta, start_delta)))


def merge_geometry(spec, before, after):
    """Return a staged spec without changing any of the supplied inputs."""
    validate_schema(spec)
    staged = copy.deepcopy(spec)
    width, height = resolve_atlas_size(spec)
    original = cubes_by_name(build_bbmodel(spec, width, height))
    baseline = cubes_by_name(before)
    edited = cubes_by_name(after)
    if list(baseline) != list(edited):
        raise SpecError("Cube names or order changed. Update parts in the source spec explicitly.")
    if baseline.keys() != original.keys() or before["resolution"] != {"width": width, "height": height}:
        raise SpecError("Baseline cubes or atlas size differ from the current source spec")
    extra = metadata_changes(before, after, {"elements"} | PROJECT_METADATA)
    if extra:
        raise SpecError(f"Project edits require explicit write-back: {', '.join(extra)}")

    parents = recover_parents(before)
    names_by_uuid = {cube["uuid"]: name for name, cube in baseline.items()}
    for part in staged["parts"]:
        name = part["id"]
        previous = geometry(baseline[name])
        current = geometry(edited[name])
        parent = names_by_uuid.get(parents[baseline[name]["uuid"]])
        if previous != geometry(original[name]) or parent != (part.get("parent") or None):
            raise SpecError(f"{name}: baseline geometry or hierarchy differs from the current source spec")
        extra = metadata_changes(baseline[name], edited[name], GEOMETRY_KEYS)
        if extra:
            raise SpecError(f"{name}: edit these fields in the source spec explicitly: {', '.join(extra)}")
        update_bounds(part, previous, current)
        generated_pivot = [_snap(value) for value in part.get("pivot", part["pos"])]
        if current["origin"] != generated_pivot:
            part["pivot"] = current["origin"]
        for cube_key, spec_key in (("rotation", "rot"), ("inflate", "inflate")):
            if current[cube_key] != previous[cube_key]:
                part[spec_key] = current[cube_key]

    validate_schema(staged)
    if resolve_atlas_size(staged) != (width, height):
        raise SpecError("Geometry edits resize the atlas. Review UVs and the atlas in the source spec.")
    rebuilt_model = build_bbmodel(staged, width, height)
    for name, cube in cubes_by_name(rebuilt_model).items():
        if geometry(cube) != geometry(edited[name]):
            raise SpecError(f"{name}: regenerated geometry differs from the edited cube. Check the generator grid.")
        if cube["faces"] != original[name]["faces"]:
            raise SpecError(f"{name}: regeneration changes the UV layout. Review UVs in the source spec.")
    findings = validate_geometry(staged)
    findings += [("warn", message) for message in validate_bbmodel(rebuilt_model, staged)]
    return staged, findings


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("spec", type=Path, help="Existing asset spec")
    parser.add_argument("before", type=Path, help="Editable project export before the edit")
    parser.add_argument("after", type=Path, help="Editable project export after the edit")
    parser.add_argument("-o", "--out", type=Path, help="New staged spec path. Omit to preview changes.")
    args = parser.parse_args()

    try:
        spec = load_json(args.spec)
        staged, findings = merge_geometry(spec, load_json(args.before), load_json(args.after))
        if args.out:
            with args.out.open("x", encoding="utf-8") as output:
                json.dump(staged, output, ensure_ascii=False, indent=2)
                output.write("\n")
    except (OSError, ValueError, KeyError, TypeError, SpecError) as error:
        parser.exit(2, f"{error}\n")

    changes = {}
    for previous, current in zip(spec["parts"], staged["parts"]):
        changed = metadata_changes(previous, current, set())
        if changed:
            changes[current["id"]] = {key: {"before": previous.get(key), "after": current[key]}
                                      for key in changed}
    print(json.dumps(changes, ensure_ascii=False, indent=2))
    for severity, message in findings:
        print(f"[{severity}] {message}")


if __name__ == "__main__":
    main()
