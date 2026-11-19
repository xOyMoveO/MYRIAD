# Blockbench preview and write-back

Use this when a MYRIAD asset has a source `*.spec.json` and the desktop Blockbench
MCP server is available. Keep the spec and any asset-specific generator as the
source of truth.

1. **Generate a preview.** Run the asset's usual generator for the exact spec
   revision being reviewed. For example, Rivet uses
   `tools/assets/generate_rivet_models.py` for its shared atlas, Java format,
   display transforms and runtime assets. Open a scratch copy of the resulting
   `.bbmodel` in Blockbench through the editor's Open Model action. Keep that
   copy's tab active for the MCP calls.
2. **Read and capture.** Load `blockbench-use` and `blockbench-modeling` if
   installed. Call `get_capabilities` with `include_tools: true` and verify the
   active copy's name and format. Use `list_outline` and, for textured models,
   `list_textures`. Call `capture_screenshot` with the project UUID. Choose at
   least one view that exposes the surface or silhouette under review. When
   setting a camera explicitly, reuse its parameters for each capture. Export
   the pre-edit snapshot with `export_model` and `codec_id: "project"`.
3. **Refine the copy.** Use the dedicated MCP tools for the active format. For
   example, use `modify_cube` with the inspected element UUID to adjust bounds
   or rotation. Capture the same view after the change and inspect the image.
   Use another view when the first hides the affected geometry. Export the
   post-edit snapshot from the same tab with the same project codec. Save the
   full editable export from each call, including when returned as an embedded
   MCP resource. A truncated preview cannot be used for write-back.
4. **Write back approved edits.** Once the user or the task's acceptance criteria
   confirm the edit, stage the change against the exact source spec for the
   pre-edit snapshot:

   ```sh
   python .agents/skills/minecraft-ai-generate-bbmodel/scripts/merge_bbmodel_geometry.py \
     source.spec.json before.bbmodel after.bbmodel -o staged.spec.json
   ```

   Omit `-o` to preview the field-level changes. The helper takes the positions,
   sizes, cube pivots, rotations and inflation of existing cuboids. It preserves
   the rest of the spec and checks that rebuilding produces the edited geometry.
   Supply both exports from the same Blockbench tab so format serialization and
   element UUIDs are consistent.

   For added, removed or renamed parts and hierarchy changes, make the
   corresponding edits to `parts` and any dependent references in the existing
   spec. For UV or texture changes, use `uv_origin`, `faces`, `atlas` and
   `face_details` where they represent the approved result. Route changes such
   as arbitrary painted pixels, display transforms or settings outside the spec
   through the asset's actual texture inputs or generator. Carry forward the
   existing atlas definitions, face details and metadata when editing the spec.
5. **Regenerate and compare.** Review the staged diff, regenerate with the
   asset's usual builder, reopen the rebuilt model, and confirm the changed
   coordinates and a matching screenshot from the same angle. Apply the verified
   diff to the source spec and produce the requested derived assets using that
   builder.
