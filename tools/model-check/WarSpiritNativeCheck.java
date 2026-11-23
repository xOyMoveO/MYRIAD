import java.nio.file.Files;
import java.nio.file.Path;
import java.io.StringReader;
import com.google.gson.JsonParser;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;
import net.minecraft.core.Direction;

/** Runs against the actual Fabric project's Minecraft client model parser. */
public class WarSpiritNativeCheck {
    public static void main(String[] args) throws Exception {
        for (String item : new String[]{"chixiao_blade", "yunsheng_bell"}) {
            var path = Path.of(args[0], "src/main/resources/assets/myriad/models/item", item + ".json");
            String json = Files.readString(path);
            var source = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("elements");
            try (var reader = new StringReader(json)) {
                var model = CuboidModel.fromStream(reader);
                var elements = ((UnbakedCuboidGeometry) model.geometry()).elements();
                int quads = 0, glow = 0, cuttingFaces = 0;
                for (int i=0; i<elements.size(); i++) {
                    var part=elements.get(i);
                    String name=source.get(i).getAsJsonObject().get("name").getAsString();
                    if (part.lightEmission() == 15) glow++;
                    for (var entry : part.faces().entrySet()) {
                        var face = entry.getValue();
                        boolean vertical = entry.getKey().getAxis() != Direction.Axis.Y;
                        float u0 = CuboidFace.getU(face.uvs(), face.rotation(), 0);
                        float u1 = CuboidFace.getU(face.uvs(), face.rotation(), 3);
                        float v0 = CuboidFace.getV(face.uvs(), face.rotation(), 0);
                        float v1 = CuboidFace.getV(face.uvs(), face.rotation(), 1);
                        // The rear bevel uses the reverse UV to keep its highlight on +X.
                        boolean outwardSilver = item.equals("chixiao_blade")
                                && entry.getKey() == Direction.SOUTH
                                && (name.startsWith("blade_bevel_") || name.startsWith("silver_edge_"));
                        if (outwardSilver) cuttingFaces++;
                        if ((vertical && !outwardSilver) != (u0 > u1) || v0 >= v1 || u0 == u1)
                            throw new IllegalArgumentException(item + ": bad UV orientation");
                        quads++;
                    }
                }
                if (glow != (item.equals("yunsheng_bell") ? 2 : 0))
                    throw new IllegalArgumentException(item + ": wrong self-lit part count");
                if (item.equals("chixiao_blade") && cuttingFaces==0)
                    throw new IllegalArgumentException(item + ": missing bevel faces");
                System.out.printf("%s: native parse, %d parts, %d faces, %d emissive elements%n",
                                  item, elements.size(), quads, glow);
            }
        }
    }
}
