---
name: minecraft-itemdisplay-orientation
description: Orient Blockbench item models carried by Minecraft ItemDisplay entities along a real 3D flight or target direction. Use for flying weapons, slash displays, or visual effects whose model axes do not match vanilla yaw and pitch.
---

# Minecraft ItemDisplay model orientation

Use this skill when an `ItemDisplay` model must continuously face a flight direction, a target, or a downward slash. It applies to NeoForge/Fabric implementations using JOML transformations; it does not replace ordinary entity rotation for models already aligned to local Z.

## Establish the model axis first

Do not assume a Blockbench item's visible front points along local `+Z` or that a sword tip is `+Y`. Inspect the model or test it in-game and record which local unit axis points at the visible tip/front. A mistaken sign reverses the model, so a sword's hilt faces its travel direction.

The historical Durandal/归剑 model used local `-Y` for its visible tip; verify the current asset before reusing that axis.

## Use transformation rotation for three-dimensional motion

`Entity#setYRot` / `setXRot` alone follows entity-facing conventions and often gives the wrong result for models built on the Y axis. For projectiles that climb, dive, or follow curved paths, use a quaternion that maps the verified model axis to the normalized world direction:

```java
Vec3 direction = velocity.normalize();
Quaternionf aim = new Quaternionf().rotationTo(
        0.0F, -1.0F, 0.0F,
        (float) direction.x, (float) direction.y, (float) direction.z
);
display.setTransformation(new Transformation(
        new Vector3f(0, 0, 0), aim,
        new Vector3f(scale, scale, scale), new Quaternionf()
));
display.setYRot(0.0F);
display.setXRot(0.0F);
```

Recalculate this transform whenever the visual's movement vector changes (normally once per tick). This makes the tip follow the actual curve, including downward strikes.

## Compose local rotations deliberately

`Transformation` combines a world-facing `leftRotation` with a model-local `rightRotation`.

- Put the axis-to-world-direction quaternion in `leftRotation`.
- Use `rightRotation` only for intentional local roll or a verified model correction.
- A local `rotateZ(180°)` flips local `-Y` to `+Y`. Do not add it on top of an axis mapping unless the result is tested; it commonly reverses a sword.
- Preserve translation and scale whenever an orientation is updated. If the runtime API does not expose the current transform, retain the scale/local-roll settings alongside the spawned display, keyed by its UUID.

## Verification checklist

Test a spawned model while it travels horizontally, vertically upward, diagonally downward, and along a curved path. In every case confirm that:

- the visible tip, not the handle, leads movement;
- downward motion visibly points down rather than staying vertical or horizontal;
- scale does not change while the orientation updates;
- disposal also clears any UUID-keyed visual-transform cache.
