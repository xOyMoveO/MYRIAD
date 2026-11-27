package com.myriad.tools;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.resources.model.cuboid.*;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

/** Checks the actual Minecraft model parser, its source geometry, atlas and hand matrices. */
public final class ThunderSwordCheck {
    static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    private static final double EPS = 1e-5;

    private static JsonObject read(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    // All modeled rotations are about Z. A rectangle's two edge normals, plus Z,
    // give the separating axes for exact cuboid contact, including rotated stones.
    record Solid(Vector3f[] corners, Vector3f[] axes) {
        static Solid of(CuboidModelElement cube) {
            var vertices = new Vector3f[8];
            for (int i = 0; i < 8; i++) {
                var vertex = new Vector3f();
                for (int axis = 0; axis < 3; axis++)
                    vertex.setComponent(axis, ((i & (1 << axis)) == 0 ? cube.from() : cube.to()).get(axis));
                if (cube.rotation() != null) {
                    var rotation = cube.rotation();
                    vertex.div(16).sub(rotation.origin());
                    rotation.transform().transformPosition(vertex);
                    vertex.add(rotation.origin()).mul(16);
                }
                vertices[i] = vertex;
            }
            return new Solid(vertices, new Vector3f[] {
                    new Vector3f(vertices[1]).sub(vertices[0]).normalize(),
                    new Vector3f(vertices[2]).sub(vertices[0]).normalize(),
                    new Vector3f(0, 0, 1)});
        }
        boolean separates(Solid other, Vector3f axis) {
            double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
            double otherLow = low, otherHigh = high;
            for (int i = 0; i < corners.length; i++) {
                double a = corners[i].dot(axis), b = other.corners[i].dot(axis);
                low = Math.min(low, a); high = Math.max(high, a);
                otherLow = Math.min(otherLow, b); otherHigh = Math.max(otherHigh, b);
            }
            return low > otherHigh + EPS || otherLow > high + EPS;
        }
        boolean touches(Solid other) {
            for (var axis : axes) if (separates(other, axis)) return false;
            for (var axis : other.axes) if (separates(other, axis)) return false;
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        Path assets = Path.of(args[0]), art = Path.of(args[1]);
        String name = "tianxing_thunder_longsword";
        Path source = art.resolve(name + ".spec.json"), modelFile = assets.resolve("models/item/leitingjian.json");
        var spec = read(source); var json = read(modelFile); var editable = read(art.resolve(name + ".bbmodel"));
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)));
        require(json.get("myriad_source_sha256").getAsString().equals(sha), "Runtime digest must match editable spec");
        var parts = spec.getAsJsonArray("parts"); var elements = json.getAsJsonArray("elements");
        var cubes = editable.getAsJsonArray("elements");
        require(elements.size() == parts.size() && cubes.size() == parts.size(), "Every source part must reach both outputs");
        require(json.get("display").equals(spec.get("display")) && json.get("display").equals(editable.get("display")),
                "Seven display slots agree across editable source and runtime");
        require(read(assets.resolve("items/leitingjian.json")).getAsJsonObject("model").get("model").getAsString()
                .equals("myriad:item/leitingjian"), "Registered item uses new model");
        require(json.getAsJsonObject("textures").get("layer0").getAsString().equals("myriad:item/" + name), "Correct runtime atlas");
        Path png = assets.resolve("textures/item/" + name + ".png");
        var texture = ImageIO.read(png.toFile());
        String data = editable.getAsJsonArray("textures").get(0).getAsJsonObject().get("source").getAsString();
        require(Arrays.equals(Base64.getDecoder().decode(data.substring(data.indexOf(',')+1)), Files.readAllBytes(png)),
                "Embedded Blockbench texture must be identical to runtime PNG");
        int resolution = spec.getAsJsonObject("atlas").get("width").getAsInt();
        require(texture.getWidth() == resolution && texture.getHeight() == resolution, "Current atlas dimensions");
        var names = new ArrayList<String>();
        var solids = new ArrayList<Solid>();
        var byName = new HashMap<String,CuboidModelElement>();
        boolean paintedGrip = false, paintedBlade = false;
        for (int i = 0; i < parts.size(); i++) {
            var part = parts.get(i).getAsJsonObject(); var element = elements.get(i).getAsJsonObject();
            var authored = cubes.get(i).getAsJsonObject();
            String id = part.get("id").getAsString(); names.add(id);
            require(id.equals(element.get("name").getAsString()) && id.equals(authored.get("name").getAsString()), "Source part identity");
            var cube = MinecraftModelParser.element(element); var solid = Solid.of(cube);
            require(byName.put(id,cube) == null,"Unique part " + id); solids.add(solid);
            for (int axis = 0; axis < 3; axis++) {
                double from = cube.from().get(axis), to = cube.to().get(axis);
                require(from < to && from >= -16 && to <= 32,"Vanilla bounds: " + id);
                require(Math.abs(from - part.getAsJsonArray("pos").get(axis).getAsDouble()) < EPS &&
                        Math.abs(to - from - part.getAsJsonArray("size").get(axis).getAsDouble()) < EPS, "Source geometry: " + id);
            }
            require(authored.get("from").equals(element.get("from")) && authored.get("to").equals(element.get("to")),
                    "Editable dimensions: " + id);
            if (part.has("rot")) {
                var rotation = element.getAsJsonObject("rotation");
                require(rotation.get("axis").getAsString().equals("z") && rotation.get("origin").equals(part.get("pivot"))
                        && rotation.get("angle").equals(part.getAsJsonArray("rot").get(2))
                        && authored.get("rotation").equals(part.get("rot")) && authored.get("origin").equals(part.get("pivot")), "Rotation: " + id);
            } else require(cube.rotation() == null, "Unexpected rotation: " + id);
            for (var face : element.getAsJsonObject("faces").entrySet()) {
                var faceJson = face.getValue().getAsJsonObject();
                require(faceJson.get("texture").getAsString().equals("#layer0"), "Texture binding: "+id);
                var uv = faceJson.getAsJsonArray("uv");
                var bbUv = authored.getAsJsonObject("faces").getAsJsonObject(face.getKey()).getAsJsonArray("uv");
                for (int j = 0; j < 4; j++) {
                    double n = uv.get(j).getAsDouble();
                    require(n >= 0 && n <= 16 && Math.abs(n * resolution / 16 - bbUv.get(j).getAsDouble()) < EPS, "UV parity: "+id);
                }
                if ((id.equals("grip") || id.equals("blade_2")) && face.getKey().equals("north")) {
                    var colors = new HashSet<Integer>();
                    int x0 = (int)(uv.get(0).getAsDouble()*resolution/16), x1 = (int)(uv.get(2).getAsDouble()*resolution/16);
                    int y0 = (int)(uv.get(1).getAsDouble()*resolution/16), y1 = (int)(uv.get(3).getAsDouble()*resolution/16);
                    for (int x=Math.min(x0,x1);x<Math.max(x0,x1);x++) for(int y=y0;y<y1;y++) colors.add(texture.getRGB(x,y));
                    if(id.equals("grip")) paintedGrip = colors.size() >= 4;
                    else paintedBlade = colors.size() >= 8;
                }
            }
        }
        require(paintedGrip && paintedBlade, "Actual grip and blade UVs sample their detailed surfaces");
        var grip = byName.get("grip");
        require(grip.from().distance(7.59375f,6.25f,7.6875f) < EPS && grip.to().distance(8.40625f,9.75f,8.3125f) < EPS,
                "Exact existing grip volume and centered palm");
        var guard = byName.get("guard_base"); require(Math.abs(guard.from().y()-10) < EPS,"Guard directly above the grip");
        for(int i=0;i<8;i++) {
            var steel=byName.get("blade_"+i); var left=byName.get("left_honed_edge_"+i); var right=byName.get("right_honed_edge_"+i);
            require(Math.abs(steel.to().z()-steel.from().z()-.125) < EPS && Math.abs(left.to().x()-steel.from().x())<EPS
                    && Math.abs(right.from().x()-steel.to().x())<EPS && Math.abs(left.from().x()+right.to().x()-16)<EPS
                    && left.to().z()-left.from().z() < steel.to().z()-steel.from().z()
                    && right.to().z()-right.from().z() < steel.to().z()-steel.from().z(),"Both +/-X edges are thinner than the +Y spine");
            require(Math.abs(steel.from().z()+steel.to().z()-16)<EPS && Math.abs(steel.from().x()+steel.to().x()-16)<EPS,"Centered blade axis");
        }
        require(Math.abs(byName.get("blade_7").to().y()-30.5)<EPS && Math.abs(byName.get("silk_strand_0").from().y()-2.5)<EPS,"Blade tip and tassel length");
        var connected = new BitSet(); var queue = new ArrayDeque<Integer>();
        int root=names.indexOf("grip"); queue.add(root); connected.set(root);
        while(!queue.isEmpty()){
            var from=solids.get(queue.removeFirst());
            for(int i=0;i<solids.size();i++) if(!connected.get(i) && from.touches(solids.get(i))) {
                connected.set(i); queue.addLast(i);
            }
        }
        for(int i=0;i<solids.size();i++) require(connected.get(i),"Detached from hilt: "+names.get(i));
        try (var reader=Files.newBufferedReader(modelFile)) {
            var model=CuboidModel.fromStream(reader); require(model.geometry()!=null,"Actual 26.3 Minecraft parser accepts item");
            for (var context:List.of(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                    ItemDisplayContext.GUI,ItemDisplayContext.GROUND,ItemDisplayContext.FIXED)) {
                var transform=model.transforms().getTransform(context); require(transform!=ItemTransform.NO_TRANSFORM,"Explicit display: "+context);
                for (boolean left:new boolean[]{false,true}) {
                    var pose=new PoseStack.Pose(); transform.apply(left,pose);
                    var point=pose.pose().transformPosition(new Vector3f(.5f));
                    var expected=new Vector3f(transform.translation()); if(left)expected.x=-expected.x;
                    require(point.isFinite() && point.distance(expected)<EPS && pose.pose().determinant3x3()>0,"Centered hand pivot and positive scale");
                    if(context==ItemDisplayContext.GUI || context==ItemDisplayContext.FIXED)
                        for(var solid:solids) for(var corner:solid.corners()){
                            var vertex=pose.pose().transformPosition(new Vector3f(corner).div(16));
                            require(Math.abs(vertex.x)<.48 && Math.abs(vertex.y)<.48,"GUI/fixed safety padding");
                        }
                }
            }
        }
        System.out.println("[MYRIAD-THUNDER-SWORD] PASS: " + names.size() + " connected cuboids, paired honed edges, original grip/tip, detailed UVs, source and embedded atlas equality, seven displays and 26.3 parser");
    }
}
