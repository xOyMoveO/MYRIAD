import com.google.gson.*;
import com.myriad.client.gui.SkillChoiceLayout;
import com.myriad.character.ability.AbilityFormulaText;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Offline composition using the real responsive layout, artwork, skill JSON and icon PNGs.
 * This is explicitly NOT a Minecraft screenshot or font/render-pipeline acceptance test. */
public final class SkillChoicePreview {
    static Graphics2D g;static final int GOLD=0xFFE8CE90,JADE=0xFF91D9CA,MUTED=0xFF869C9F;
    static void fill(int x,int y,int w,int h,int color){g.setColor(new Color(color,true));g.fillRect(x,y,w,h);}
    static void text(String s,int x,int y,int w,int color){
        var fm=g.getFontMetrics();if(fm.stringWidth(s)>w){while(!s.isEmpty()&&fm.stringWidth(s+"…")>w)s=s.substring(0,s.length()-1);s+="…";}
        g.setColor(new Color(color,true));g.drawString(s,x,y+8);
    }
    static void plate(SkillChoiceLayout.Box b,int border,int color){fill(b.x(),b.y(),b.w(),b.h(),border);fill(b.x()+1,b.y()+1,b.w()-2,b.h()-2,color);fill(b.x()+3,b.y()+3,b.w()-6,1,0x2949A99D);}
    static void button(SkillChoiceLayout.Box b,String s,boolean enabled,boolean selected){
        plate(b,selected?GOLD:enabled?0xFF50877E:0xFF34444B,selected?0xF02C4D46:enabled?0xF016373B:0xF0122028);
        text(s,b.x()+(b.w()-g.getFontMetrics().stringWidth(s))/2,b.y()+(b.h()-9)/2,b.w()-8,enabled?GOLD:MUTED);
        if(selected)fill(b.x()+8,b.bottom()-3,b.w()-16,1,GOLD);
    }
    public static void main(String[] args)throws Exception{
        Path project=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        var resources=project.resolve("src/main/resources");var assets=resources.resolve("assets/myriad");
        var defs=new ArrayList<JsonObject>();
        for(String group:List.of("first_group","second_group"))for(var node:JsonParser.parseString(Files.readString(resources.resolve("data/myriad/role_rework/"+group+".json"))).getAsJsonObject().getAsJsonArray("skills"))defs.add(node.getAsJsonObject());
        var lang=JsonParser.parseString(Files.readString(assets.resolve("lang/zh_cn.json"))).getAsJsonObject();
        var icons=new HashMap<String,String>();var matcher=Pattern.compile("Map.entry\\(\"([^\"]+)\", \"([^\"]+)\"\\)").matcher(Files.readString(project.resolve("src/main/java/com/myriad/client/SkillIcon.java")));
        while(matcher.find())icons.put(matcher.group(1),matcher.group(2));
        var art=ImageIO.read(assets.resolve("textures/gui/skill_choice/meridian_hall.png").toFile());
        for(String role:List.of("lingyuan","yingxiao"))for(boolean small:new boolean[]{false,true}){
            int width=small?320:640,height=small?240:360,scale=small?3:2;
            var layout=SkillChoiceLayout.create(width,height,small?9999:0,small?9999:0);var panel=layout.panel();var view=layout.viewport();
            var image=new BufferedImage(width*scale,(height+24)*scale,BufferedImage.TYPE_INT_RGB);g=image.createGraphics();g.scale(scale,scale);
            g.setFont(new Font("Microsoft YaHei",Font.PLAIN,9));g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            fill(0,0,width,height+24,0xFF071116);text("离线布局预览 / 非游戏截图 / 实际布局与资源，字体近似",12,7,width-24,0xFFB9CFC9);g.translate(0,24);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(art,panel.x(),panel.y(),panel.w(),panel.h(),null);
            fill(panel.x()+18,panel.y()+18,panel.w()-36,36,0xB0091B21);
            text("角色修行 · "+lang.get("character.myriad."+role).getAsString(),panel.x()+26,panel.y()+22,panel.w()-72,GOLD);
            text("等级 10/20   技能点 1   金币 1400",panel.x()+26,panel.y()+37,panel.w()-52,JADE);
            button(new SkillChoiceLayout.Box(panel.right()-34,panel.y()+18,16,16),"×",true,false);
            for(int i=0;i<3;i++)button(layout.tab(i),new String[]{"技能分支","五属性成长","战术补给"}[i],true,i==0);
            var saved=g.getClip();g.clipRect(view.x(),view.y(),view.w(),view.h());
            for(int c=0;c<4;c++){var b=layout.column(c);String heading=new String[]{"被动","常规","主要","终极"}[c],rule=c==1?"两项均可学习":"二选一 · 首学锁定";
                text(heading,b.x()+Math.max(5,(b.w()-g.getFontMetrics().stringWidth(heading))/2),b.y()+1,b.w()-10,GOLD);
                text(rule,b.x()+Math.max(5,(b.w()-g.getFontMetrics().stringWidth(rule))/2),b.y()+13,b.w()-10,JADE);fill(b.x()+5,b.y()+24,b.w()-10,1,0x775D9D91);}
            int[] ranks={2,0,2,1,3,0,0,1};
            var skills=defs.stream().filter(s->s.get("role").getAsString().equals(role)).sorted(Comparator.comparingInt(s->s.get("index").getAsInt())).toList();
            for(var skill:skills){int i=skill.get("index").getAsInt(),rank=ranks[i];var b=layout.card(i);boolean locked=i==1||i==5||i==6;
                if(!b.overlaps(view))continue;
                int edge=locked?0xFF34454B:rank>0?GOLD:0xFF3C716F;plate(b,edge,locked?0xEF101B23:rank>0?0xF0173439:0xF00A1C22);
                if(rank>0)fill(b.x()+2,b.y()+7,2,b.h()-14,GOLD);
                var icon=ImageIO.read(assets.resolve("textures/gui/sprites/skill_icons/generated/"+icons.get(skill.get("iconAlias").getAsString())+".png").toFile());
                g.drawImage(icon,b.x()+8,b.y()+8,26,26,null);
                text(skill.get("name").getAsString(),b.x()+42,b.y()+9,b.w()-50,locked?MUTED:GOLD);
                text((rank>0?"已学 ":"未学 ")+rank+" / "+skill.get("maxRank").getAsInt(),b.x()+42,b.y()+23,b.w()-50,locked?MUTED:JADE);
                String summary=AbilityFormulaText.evaluate(skill.get("description").getAsString(),Math.max(1,rank),100);int lineY=b.y()+38;
                for(int line=0;line<(b.h()>=104?2:1)&&!summary.isEmpty();line++){
                    int count=0;while(count<summary.length()&&g.getFontMetrics().stringWidth(summary.substring(0,count+1))<=b.w()-16)count++;
                    text(summary.substring(0,count),b.x()+8,lineY,b.w()-16,locked?MUTED:0xFFC5D5D2);summary=summary.substring(count);lineY+=10;
                }
                String costs=i<2?"被动，无蓝耗":"蓝耗 "+skill.get("mana").getAsInt()+" · CD "+String.format(Locale.ROOT,"%.1f",skill.getAsJsonArray("cooldowns").get(0).getAsInt()/20.0)+"s";
                text(costs,b.x()+8,b.bottom()-38,b.w()-16,0xFF9DC2D0);button(layout.action(i),locked?"互斥":rank==0?"选择":"+1",!locked,false);
            }
            g.setClip(saved);
            if(layout.maxX()>0){var track=layout.horizontalTrack();var thumb=layout.horizontalThumb();fill(track.x(),track.y(),track.w(),track.h(),0xAA071418);fill(thumb.x(),thumb.y(),thumb.w(),thumb.h(),0xBBAEBA8C);}
            if(layout.maxY()>0){var track=layout.verticalTrack();var thumb=layout.verticalThumb();fill(track.x(),track.y(),track.w(),track.h(),0xAA071418);fill(thumb.x(),thumb.y(),thumb.w(),thumb.h(),0xBBAEBA8C);}
            fill(panel.x()+18,panel.bottom()-20,panel.w()-36,14,0xED091B21);
            text("被动/主要/终极二选一 · 常规可兼修 · 滚轮或拖动滚动条查看",panel.x()+24,panel.bottom()-18,panel.w()-48,MUTED);
            g.dispose();Path target=out.resolve(role+(small?"-small-scrolled":"-horizontal")+".png");ImageIO.write(image,"PNG",target.toFile());System.out.println(target);
        }
    }
}
