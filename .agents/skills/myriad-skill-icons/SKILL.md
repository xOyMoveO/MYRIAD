---
name: myriad-skill-icons
description: "Generate and integrate consistent 64×64 skill-icon sets for the MYRIAD NeoForge mod when adding or refreshing character skills and branch variants."
---

# MYRIAD Skill Icons

Use this skill when the user asks to create, regenerate, or apply character skill icons in the current MYRIAD checkout. Resolve project paths from the root containing `AGENTS.md` and `gradle.properties`; read those files and `docs/agents/domain.md` before integration.

## Required visual contract

- Resolve the requested character's actual skill IDs and branch variants from current definitions. For a selected loadout, use **passive, Q, W, E, R** order. For a full candidate set, include every requested branch and record the exact ID-to-cell mapping; do not assume five icons cover all candidates.
- Generate a selected five-skill loadout as one horizontal **1×5 grid**. For other counts, choose a declared grid or split into batches without omitting branch variants. Each cell must be an independent, centered, square icon that can be cropped without overlapping a neighbor.
- Export each cell as a **64×64 PNG**. Use non-transparent artwork: a dark, role-appropriate textured background is required.
- Each icon includes its own compact border: dark metal outer edge, readable inner line, and thematic corner accents. The HUD may add a subtle state outline, but the asset must already look complete by itself.
- Style: crisp pixel art, with subject and motifs matching the character's theme; use Eastern xuanhuan only for characters whose theme calls for it. Prioritize a clear silhouette and readable high-contrast subject at 64×64. Do not include text, numbers, UI hotkeys, watermarks, or character portraits.
- Keep the images in each set visually unified: shared palette, border construction, lighting direction, and detail density. Different skills must remain distinguishable by their core symbol and action.

## Source information and prompt design

1. Read the role's available project documentation and actual skill implementation before generating; external planning files are optional when absent. Use the skill name and effect description to define each icon's central visual symbol.
2. State the left-to-right grid order in the prompt. Preserve the mapping exactly when cropping.
3. Use the built-in image generator. Ask for opaque backgrounds and the complete border in the generated cells; do not rely on a transparent checkerboard or a separate full-sheet frame.
4. If a prior role set is available, match its visual language rather than introducing a new style. Ice, fire, wind, lightning, sword, talisman, and shadow roles may differ in palette and motif but retain the same border weight and icon density.

## Integration

- Save the final files to:
  `src/main/resources/assets/myriad/textures/gui/sprites/skill_icons/generated/<character-id>/`
- Match filenames to the current `SkillIcon` mapping and lowercase skill IDs; use `passive.png` only where that existing mapping requires it. Keep distinct branch IDs mapped to the correct files.
- Add or verify the matching `SkillIcon.GENERATED_ICON_PATHS` entries for every requested skill ID. Buff displays reuse the same mapping, so do not create separate Buff-only art.
- Inspect the finished grid before cropping. Do not assume the generator produced mathematically equal-width cells: use the visible dark separator bands and each cell's complete outer metal border to measure the exact left/right bounds of all panels.
- Crop every cell only between its own measured outer-border bounds. Never derive later cell positions from a fixed pitch after measuring the first cell; generated gutters and panel widths can vary and otherwise introduce adjacent-cell pollution.
- Export an opaque 64×64 PNG that fills the whole square skill slot. After the exact border crop, resize the full panel directly to 64×64 with nearest-neighbor sampling; do not letterbox or add side padding. A small aspect adjustment is preferable to unused black margins in the HUD.
- Inspect all four edges of every output at native 64×64 before integration: each image must fill the square, retain its own compact border, and contain no motif, border segment, or glow from either neighboring cell.
- Build with the project Gradle Wrapper; use offline mode only when dependencies are cached. Deploy only according to root `AGENTS.md`, including its identity-migration exception, confirmed target instance, backup, runtime-state and hash checks.

## Handoff

Report the character, the mapped skills and branch coverage, saved asset directory, and whether the JAR was deployed. If the user only asked for a preview, do not replace project assets or build.
