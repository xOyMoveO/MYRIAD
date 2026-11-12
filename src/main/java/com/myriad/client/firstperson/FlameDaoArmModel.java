package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Rigid skin fist and a short visible forearm continuing below the view; no visible elbow rig. */
public final class FlameDaoArmModel {
    private record Band(ModelPart.Cube cube,ModelPart.Polygon[] rest){}
    private record Tail(Matrix4f inverse,Vector3f start,Vector3f c1,Vector3f c2,Vector3f end,
                        Quaternionf wrist,Quaternionf toward,float centre,float endY){}
    public static final class Skin {
        private final ModelPart model;
        private final List<Band> bands;
        private final boolean slim,left;
        private final float endY;
        private Skin(ModelPart model,List<Band> bands,boolean slim,boolean left,float endY){
            this.model=model;this.bands=List.copyOf(bands);this.slim=slim;this.left=left;this.endY=endY;
        }
        public ModelPart model(){return model;}
        public void pose(FlameDaoMotion.Pose pose,float equip){
            pose(FlameDaoRig.hand(pose,equip,left));
        }
        public void pose(Matrix4f hand){
            var lower=new Matrix4f(hand).mul(FlameDaoRig.arm(slim,left));
            float centre=(left?1:-1)*(slim?.5f:1);
            var start=lower.transformPosition(new Vector3f(centre/16,6f/16,0));
            var end=FlameDaoRig.forearmEnd(left);
            var tangent=lower.transformDirection(new Vector3f(0,-1,0)).normalize();
            var rotation=lower.getUnnormalizedRotation(new Quaternionf());
            var toward=new Quaternionf().rotationTo(tangent,new Vector3f(end).sub(start).normalize()).mul(new Quaternionf(rotation));
            var tail=new Tail(new Matrix4f(lower).invert(),start,new Vector3f(start).fma(.055f,tangent),
                    new Vector3f(start).lerp(end,.72f).add(0,-.10f,0),end,rotation,toward,centre,endY);
            var rings=new HashMap<Float,Matrix4f>();
            for(var band:bands)for(int face=0;face<band.rest.length;face++){
                var original=band.rest[face];var vertices=new ModelPart.Vertex[4];
                for(int i=0;i<4;i++){
                    var v=original.vertices()[i];
                    var p=v.y()>=6?new Vector3f(v.x(),v.y(),v.z()):rings.computeIfAbsent(v.y(),y->ring(y,tail))
                            .transformPosition(new Vector3f(v.x(),0,v.z())).mul(16);
                    vertices[i]=new ModelPart.Vertex(p.x,p.y,p.z,v.u(),v.v());
                }
                var normal=cross(vertices);var reference=cross(original.vertices());
                if(normal.lengthSquared()>1E-10&&reference.lengthSquared()>1E-10){
                    if(reference.dot(original.normal())<0)normal.negate();normal.normalize();
                }else normal.set(original.normal());
                band.cube.polygons[face]=new ModelPart.Polygon(vertices,normal);
            }
        }
    }
    private FlameDaoArmModel(){}
    private static Vector3f cross(ModelPart.Vertex[] v){
        return new Vector3f(v[1].x()-v[0].x(),v[1].y()-v[0].y(),v[1].z()-v[0].z())
                .cross(v[2].x()-v[0].x(),v[2].y()-v[0].y(),v[2].z()-v[0].z());
    }
    private static Matrix4f ring(float y,Tail tail){
        float t=Math.clamp((6-y)/(6-tail.endY),0,1),s=1-t;
        var centre=new Vector3f(tail.start).mul(s*s*s).fma(3*s*s*t,tail.c1)
                .fma(3*s*t*t,tail.c2).fma(t*t*t,tail.end);
        var rotation=new Quaternionf(tail.wrist).slerp(tail.toward,t*t*(3-2*t));
        return new Matrix4f(tail.inverse).translate(centre).rotate(rotation)
                .scale(FlameDaoRig.VIEW_SCALE/16).translate(-tail.centre,0,0);
    }
    public static Skin bake(boolean slim,boolean left){
        String arm=left?"left_arm":"right_arm",sleeve=left?"left_sleeve":"right_sleeve";
        var source=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot().getChild(arm);
        var body=new ArrayList<ModelPart.Cube>();var cloth=new ArrayList<ModelPart.Cube>();var bands=new ArrayList<Band>();
        float[] end={Float.POSITIVE_INFINITY};
        source.visit(new PoseStack(),(pose,path,index,cube)->{
            end[0]=Math.min(end[0],cube.minY);
            for(int i=0;i<12;i++){
                float step=(cube.maxY-cube.minY)/12;
                var band=band(cube,cube.minY+i*step,i==11?cube.maxY:cube.minY+(i+1)*step,i==0,i==11);
                bands.add(band);(path.isEmpty()?body:cloth).add(band.cube);
            }
        });
        return new Skin(new ModelPart(body,Map.of(sleeve,new ModelPart(cloth,Map.of()))),bands,slim,left,end[0]);
    }
    private static Band band(ModelPart.Cube source,float lo,float hi,boolean first,boolean last){
        var cube=new ModelPart.Cube(0,0,source.minX,lo,source.minZ,
                source.maxX-source.minX,hi-lo,source.maxZ-source.minZ,0,0,0,false,64,64,EnumSet.allOf(Direction.class));
        var rest=new ModelPart.Polygon[source.polygons.length];
        for(int f=0;f<source.polygons.length;f++){
            var polygon=source.polygons[f];var vertices=new ModelPart.Vertex[4];
            for(int i=0;i<4;i++){
                var v=polygon.vertices()[i];float y=Math.clamp(v.y(),lo,hi),u=v.u(),uv=v.v();
                if(y!=v.y())for(var edge:polygon.vertices()){
                    if(edge.x()==v.x()&&edge.z()==v.z()&&edge.y()!=v.y()){
                        float t=(y-v.y())/(edge.y()-v.y());u=v.u()+(edge.u()-v.u())*t;uv=v.v()+(edge.v()-v.v())*t;break;
                    }
                }
                vertices[i]=new ModelPart.Vertex(v.x(),y,v.z(),u,uv);
            }
            // Internal caps collapse to zero area: no overlapping elbow boxes or separate collar.
            boolean cap=Math.abs(polygon.normal().y())>.5f;
            float sourceY=polygon.vertices()[0].y();
            if(cap&&!((first&&sourceY<=source.minY+.001f)||(last&&sourceY>=source.maxY-.001f)))
                Arrays.fill(vertices,vertices[0]);
            cube.polygons[f]=rest[f]=new ModelPart.Polygon(vertices,polygon.normal());
        }
        return new Band(cube,rest);
    }
}
