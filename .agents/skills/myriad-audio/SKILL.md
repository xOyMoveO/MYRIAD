---
name: myriad-audio
description: "Add, organize, and play sound effects in the 万象（MYRIAD） NeoForge mod. Use for Minecraft .ogg assets, sounds.json entries, SoundEvent registration, and gameplay-triggered audio; do not use for generating raw audio waveforms."
---

# MYRIAD Audio

Use this skill for audio work in the current MYRIAD checkout (NeoForge, mod ID `myriad`). Resolve project paths from the root containing `AGENTS.md` and `gradle.properties`; read those files and `docs/agents/domain.md` before integration. Read version requirements from the current build configuration. When the source sound needs a specification or generator prompt, use an available audio-generation skill or connected generator; `$audio-sfx-generation` is optional and may be absent.

## Asset contract

- Store game sound files in `src/main/resources/assets/myriad/sounds/` as lowercase `.ogg` files. Use a readable category folder when it improves maintenance, for example `sounds/skill/yingxi_cast.ogg`.
- Add every logical sound event to `src/main/resources/assets/myriad/sounds.json`. The event ID, JSON path, and Java identifier must agree.
- Minecraft sound references omit the `sounds/` prefix and `.ogg` extension; an asset at `sounds/skill/yingxi_cast.ogg` is referenced as `myriad:skill/yingxi_cast`.
- Prefer mono effects and keep peak level below clipping. Short, distinctive feedback takes priority over high fidelity.

## Integration workflow

1. Inspect the triggering skill, block, GUI, or server event and decide whether everyone nearby, only the actor, or only a client should hear it.
2. Add the `.ogg` asset and `sounds.json` event entry.
3. Register a `SoundEvent` only when code needs a typed reusable event; otherwise use the established project style. Keep registration in a dedicated registry class rather than mixing it into unrelated gameplay classes.
4. Play world-affecting sounds from the logical server so nearby players hear them consistently. Use client-only playback only for personal UI, prediction, or local camera feedback.
5. Select `SoundSource` to match the intent (`PLAYERS`, `BLOCKS`, `HOSTILE`, or UI), with intentional volume and pitch ranges.
6. Build the mod and, when possible, test the exact trigger in a client. Confirm the resource path has no missing-sound warning.

## Naming

Use `<system>_<action>` event IDs, such as `skill_yingxi_cast`, `skill_yingxi_hit`, `spirit_channel_start`, `spirit_channel_complete`, `ui_skill_offer`, and `ui_skill_select`.

For repeated casts, reference multiple similarly named variants from `sounds.json` with weights instead of adding randomness in unrelated gameplay code.

## Scope boundary

This skill integrates and verifies sound assets; it does not invent or synthesize audio. If the requested source sound does not exist, prepare its creative specification and use an available audio-generation skill or connected audio generator, then return here to integrate it.
