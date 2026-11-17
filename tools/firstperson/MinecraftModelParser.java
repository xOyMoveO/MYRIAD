package com.myriad.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.StringReader;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.cuboid.CuboidModelElement;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;

/** Uses the public vanilla model parser instead of a loader-widened internal GSON field. */
final class MinecraftModelParser {
    private MinecraftModelParser() { }

    static CuboidModelElement element(JsonElement element) {
        var elements = new JsonArray();
        elements.add(element);
        var model = new JsonObject();
        model.add("elements", elements);
        return ((UnbakedCuboidGeometry) CuboidModel.fromStream(new StringReader(model.toString())).geometry())
                .elements().getFirst();
    }
}
