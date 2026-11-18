package com.myriad.client;

import com.myriad.game.TeamSignalKind;
import com.myriad.network.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

public final class TeamSignalClient {
    private static TeamSignalsPayload snapshot;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static long updated,lastSound=-100;
    private static final Set<Long> seen=new HashSet<>();
    private TeamSignalClient(){}
    public static void reset(){snapshot=null;world=null;seen.clear();updated=0;lastSound=-100;}
    public static boolean available(){var mc=Minecraft.getInstance();return ClientDataHolder.modEnabled()&&mc.player!=null&&mc.level!=null
            &&ClientDataHolder.get()!=null&&ClientDataHolder.get().factionId()>0
            &&net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(TeamSignalRequest.TYPE)
            &&com.myriad.game.TeamSignalRules.allowed(ClientDataHolder.modEnabled(),ClientDataHolder.isTraining(),ClientDataHolder.phase())
            &&com.myriad.character.rework.FirstGroupSkills.active(ClientDataHolder.characterId())
            &&(mc.player.isAlive()&&!mc.player.isSpectator()||mc.player.isSpectator()
                &&ClientDataHolder.buffTicks("hero_respawn")>0&&mc.getCameraEntity()!=mc.player);}
    public static void receive(TeamSignalsPayload p){var mc=Minecraft.getInstance();
        if(!available()||!p.dimension().equals(mc.level.dimension().identifier().toString())||p.faction()!=ClientDataHolder.get().factionId()){reset();return;}
        if(world!=mc.level)reset(); world=mc.level;updated=world.getGameTime();snapshot=p;
        TeamSignalsPayload.Entry newest=null;
        for(var e:p.entries())if(seen.add(e.serial()))newest=e;
        if(seen.size()>256){seen.clear();for(var e:p.entries())seen.add(e.serial());}
        if(newest!=null&&updated-lastSound>=8){var kind=TeamSignalKind.values()[newest.kind()];
            mc.player.playSound(kind==TeamSignalKind.DANGER||kind==TeamSignalKind.RETREAT?SoundEvents.NOTE_BLOCK_BELL.value():SoundEvents.NOTE_BLOCK_PLING.value(),.45f,kind.pitch);lastSound=updated;}
    }
    public static List<TeamSignalsPayload.Entry> entries(){var mc=Minecraft.getInstance();
        if(!available()||world!=mc.level||snapshot==null||snapshot.faction()!=ClientDataHolder.get().factionId()
                ||!snapshot.dimension().equals(mc.level.dimension().identifier().toString()))return List.of();
        long age=mc.level.getGameTime()-updated;
        if(age<0||age>12)return List.of();return snapshot.entries().stream().filter(e->e.remaining()>age).toList();
    }
    public static Component label(TeamSignalsPayload.Entry e){return Component.translatable(TeamSignalKind.values()[e.kind()].key());}
    public static void render(GuiGraphicsExtractor gui,Font font){var mc=Minecraft.getInstance();var entries=entries();if(entries.isEmpty())return;
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();var camera=mc.gameRenderer.mainCamera();
        for(var e:entries){Vec3 point=e.position().add(0,.65,0);if(point.distanceToSqr(camera.position())>96*96
                    ||point.subtract(camera.position()).dot(new Vec3(camera.forwardVector()))<=0)continue;
            var obstacle=mc.level.clip(new net.minecraft.world.level.ClipContext(camera.position(),point,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
            if(obstacle.getType()!=net.minecraft.world.phys.HitResult.Type.MISS&&obstacle.getLocation().distanceToSqr(point)>.7)continue;
            var projected=mc.gameRenderer.projectPointToScreen(point);int x=(int)((projected.x+1)*.5*w),y=(int)((1-projected.y)*.5*h);
            if(x<20||x>w-20||y<20||y>h-50)continue;
            int color=TeamSignalKind.values()[e.kind()].color;
            if(Math.abs(x-w/2)<36&&Math.abs(y-h/2)<32){gui.fill(x,y-20,x+1,y-7,color);y-=26;}
            glyph(gui,x,y,color,6,e.kind());String text=font.plainSubstrByWidth(label(e).getString(),Math.max(40,w-12));int tw=font.width(text);
            x=Math.clamp(x,tw/2+3,Math.max(tw/2+3,w-tw/2-3));
            gui.fill(x-tw/2-3,y-22,x+tw/2+3,y-10,0xB3101823);gui.text(font,text,x-tw/2,y-20,color);
        }
        int y=Math.max(48,(int)(h*.58));int start=Math.max(0,entries.size()-3);
        for(int i=start;i<entries.size();i++){var e=entries.get(i);String text=e.name()+" · "+label(e).getString();
            text=font.plainSubstrByWidth(text,Math.max(80,Math.min(230,w/2-12)));
            gui.fill(6,y-2,12+font.width(text),y+10,0xA8101823);gui.text(font,text,9,y,TeamSignalKind.values()[e.kind()].color);y+=15;}
    }
    public static void glyph(GuiGraphicsExtractor g,int x,int y,int color,int r){
        glyph(g,x,y,color,r,5);
    }
    public static void glyph(GuiGraphicsExtractor g,int x,int y,int color,int r,int kind){
        g.fill(x-r-1,y-r-1,x+r+2,y-r+1,0xDD071019);g.fill(x-r,y-r,x+r+1,y-r+2,color);
        g.fill(x-r,y+r-1,x+r+1,y+r+1,color);g.fill(x-r,y-r,x-r+2,y+r+1,color);g.fill(x+r-1,y-r,x+r+1,y+r+1,color);
        switch(kind){
            case 0,1->{int d=kind==0?-1:1;g.fill(x,y-2,x+1,y+3,color);
                g.fill(x-2,y+d*2,x+3,y+d*2+1,color);g.fill(x-1,y+d*3,x+2,y+d*3+1,color);}
            case 2,3->{g.fill(x-2,y,x+3,y+1,color);g.fill(x,y-2,x+1,y+3,color);
                if(kind==2){g.fill(x-2,y-2,x-1,y-1,color);g.fill(x+2,y+2,x+3,y+3,color);}}
            case 4->{g.fill(x-1,y-3,x+2,y-2,color);g.fill(x+1,y-2,x+2,y,color);g.fill(x,y,x+1,y+1,color);g.fill(x,y+2,x+1,y+3,color);}
            case 6->{g.fill(x-2,y-3,x-1,y+3,color);g.fill(x-1,y-3,x+3,y,color);}
            default->{g.fill(x,y-3,x+1,y+1,color);g.fill(x,y+2,x+1,y+3,color);}
        }
    }
}
