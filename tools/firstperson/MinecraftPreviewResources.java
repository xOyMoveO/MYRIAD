package com.myriad.tools;

import java.nio.file.Path;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;

/** Opens the same client JAR that supplies the current Fabric development classpath. */
public final class MinecraftPreviewResources {
    private MinecraftPreviewResources() { }

    public static ZipFile open() throws Exception {
        var location = Minecraft.class.getProtectionDomain().getCodeSource().getLocation();
        return new ZipFile(Path.of(location.toURI()).toFile());
    }

    public static void main(String[] args) throws Exception {
        try (var jar = open()) {
            for (String entry : new String[]{"assets/minecraft/textures/entity/player/wide/steve.png",
                    "net/minecraft/client/renderer/entity/layers/ItemInHandLayer.class", "version.json"}) {
                if (jar.getEntry(entry) == null) throw new IllegalStateException("Missing current client resource: " + entry);
            }
            try (var reader = new java.io.InputStreamReader(jar.getInputStream(jar.getEntry("version.json")),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                String version = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().get("name").getAsString();
                if (!version.equals(args[0])) throw new IllegalStateException("Minecraft version mismatch: " + version);
                System.out.println("MINECRAFT_PREVIEW_RESOURCES_PASS version=" + version + " jar=" + jar.getName());
            }
        }
    }
}
