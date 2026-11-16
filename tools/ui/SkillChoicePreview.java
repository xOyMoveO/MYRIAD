import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.myriad.character.ability.AbilityFormulaText;
import com.myriad.client.gui.SkillChoiceLayout;
import com.myriad.client.gui.SkillChoiceLayout.Box;
import com.myriad.client.gui.SkillChoiceTooltipLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Offline composition with real layout, JSON and PNG assets. Example ranks/gold and an
 * approximate desktop font: this is not a Minecraft screenshot or an input acceptance test.
 * Usage: SkillChoicePreview <project> <output-directory> [hero-id]. */
public final class SkillChoicePreview {
    private static final int GOLD=0xFFE8CE90, JADE=0xFF91D9CA, MUTED=0xFF869C9F, INK=0xF00A1C22;
    private static final String[] SUPPLIES={"blood_pill","mana_pill","spirit_snare","scout_totem","true_sight_charm"};
    private static final String[] ATTRIBUTES={"strength","wisdom","speed","defense","cooldown"};
    private static final String[] ATTRIBUTE_ICONS={"physical_attack","spell_power","movement_speed","physical_defense","cooldown_reduction"};
    private static Graphics2D graphics;
    private static Path assets;
    private static JsonObject language;
    private static final HashMap<String,String> icons=new HashMap<>();
    private static final HashMap<String,Integer> prices=new HashMap<>();
    private static String tr(String key,Object... args){return language.get(key).getAsString().formatted(args);}
    private static void fill(int x,int y,int w,int h,int color){graphics.setColor(new Color(color,true));graphics.fillRect(x,y,w,h);}
    private static void text(String value,int x,int y,int width,int color){
        var metrics=graphics.getFontMetrics();
        if(metrics.stringWidth(value)>width){while(!value.isEmpty()&&metrics.stringWidth(value+"…")>width)value=value.substring(0,value.length()-1);value+="…";}
        graphics.setColor(new Color(color,true));graphics.drawString(value,x,y+8);
    }
    private static List<String> wrap(String value,int width){
        var result=new ArrayList<String>();
        while(!value.isEmpty()){
            int count=1;while(count<value.length()&&graphics.getFontMetrics().stringWidth(value.substring(0,count+1))<=width)count++;
            result.add(value.substring(0,count));value=value.substring(count);
        }
        return result;
    }
    private static void scaledText(String value,int x,int y,int width,int color,float scale){
        var saved=graphics.getTransform();graphics.translate(x,y);graphics.scale(scale,scale);
        text(value,0,0,Math.max(1,(int)(width/scale)),color);graphics.setTransform(saved);
    }
    private static void diamond(int x,int y,int color){
        fill(x,y-3,1,2,color);fill(x-2,y-1,2,2,color);fill(x+1,y-1,2,2,color);fill(x,y+1,1,2,color);
    }
    private static void divider(int left,int right,int y,int color){
        int middle=(left+right)/2;fill(left,y,Math.max(0,middle-4-left),1,color);
        fill(middle+5,y,Math.max(0,right-middle-5),1,color);diamond(middle,y,color);
    }
    private static void plate(Box b,int border,int fill){
        fill(b.x(),b.y(),b.w(),b.h(),border);fill(b.x()+1,b.y()+1,b.w()-2,b.h()-2,fill);
        fill(b.x()+3,b.y()+3,b.w()-6,1,0x2949A99D);
    }
    private static void button(Box b,String label,boolean selected){
        plate(b,selected?GOLD:0xFF50877E,selected?0xF02C4D46:0xF016373B);
        text(label,b.x()+(b.w()-graphics.getFontMetrics().stringWidth(label))/2,b.y()+(b.h()-9)/2,b.w()-8,GOLD);
    }
    private static void png(String path,Box b)throws Exception{
        var image=ImageIO.read(assets.resolve("textures/gui/sprites/"+path+".png").toFile());
        graphics.drawImage(image,b.x(),b.y(),b.w(),b.h(),null);
    }
    private static void navigation(Box b,int index){
        int x=b.x()+4,y=b.y()+4,w=b.w()-8,h=b.h()-8,cx=x+w/2,cy=y+h/2;
        if(index==0){
            fill(cx-1,y+2,2,h-4,JADE);fill(x+2,cy-1,w-4,2,JADE);
            for(int[] p:new int[][]{{cx-3,y},{x,cy-3},{x+w-6,cy-3},{cx-3,y+h-6}})fill(p[0],p[1],6,6,JADE);
        }else if(index==1){
            for(int i=0;i<5;i++)fill(x+i*Math.max(2,w/5),y+h-4-i%3*4,2,4+i%3*4,JADE);
        }else{
            fill(x+3,y+4,w-6,2,JADE);fill(x+3,y+5,2,h-6,JADE);fill(x+w-5,y+5,2,h-6,JADE);
            fill(x+3,y+h-2,w-6,2,JADE);fill(cx-1,cy-3,2,8,JADE);fill(cx-4,cy,8,2,JADE);
        }
    }
    private static void render(Path output,String hero,List<JsonObject> skills,BufferedImage background,
            int width,int height,int page,boolean unselected,boolean tooltip,boolean tooltipEnd)throws Exception{
        int scale=2,requested=0;
        if(tooltip){
            var longest=skills.stream().max(Comparator.comparingInt(s->s.get("description").getAsString().length())).orElseThrow();
            var initial=SkillChoiceLayout.create(width,height,0);var anchor=initial.card(longest.get("index").getAsInt());
            requested=anchor.y()+anchor.h()/2-initial.viewport().y()-initial.viewport().h()/2;
        }
        var layout=SkillChoiceLayout.create(width,height,page==0?requested:0);var panel=layout.panel();var view=layout.viewport();
        int maximum=page==0?layout.maxY():page==1?layout.attributeMaximum():Math.max(0,160-view.h());
        int scroll=Math.clamp(requested,0,maximum);
        var image=new BufferedImage(width*scale,(height+24)*scale,BufferedImage.TYPE_INT_RGB);
        graphics=image.createGraphics();graphics.scale(scale,scale);graphics.setFont(new Font("PingFang SC",Font.PLAIN,9));
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        fill(0,0,width,height+24,0xFF071116);
        text("离线布局示意 / 非游戏截图 / 示例等级与金币 / 字体近似",12,7,width-24,JADE);graphics.translate(0,24);
        graphics.drawImage(background,panel.x(),panel.y(),panel.w(),panel.h(),null);
        int side=Math.max(24,panel.w()*13/100);float headerScale=panel.w()<240?.9f:1;
        scaledText(tr("screen.myriad.g1.title")+" · "+tr("character.myriad."+hero),panel.x()+side,layout.titleY(),panel.w()-side-48,GOLD,headerScale);
        scaledText(tr(panel.w()<240?"screen.myriad.g1.status_compact":"screen.myriad.g1.status",10,1,1400),panel.x()+side,layout.statusY(),panel.w()-2*side,JADE,headerScale);
        button(new Box(panel.right()-40,layout.titleY(),16,16),"×",false);
        divider(panel.x()+side,panel.right()-side,layout.headerDividerY(),0xFF69B7A5);
        int navX=layout.navigationDividerX();fill(navX,view.y()+2,1,view.h()-4,0x99569B8B);
        diamond(navX,view.y()+2,0xFF69B7A5);diamond(navX,view.bottom()-2,0xFF69B7A5);
        for(int i=0;i<3;i++){button(layout.tab(i),"",page==i);navigation(layout.tab(i),i);}
        var saved=graphics.getClip();graphics.clipRect(view.x(),view.y(),view.w(),view.h());
        if(page==0){
            String[] groups={"passive","regular","main","ultimate"};
            for(int group=0;group<4;group++){
                var b=layout.category(group);
                int titleY=b.y()+Math.max(1,(b.h()-9)/2-3);
                String name=tr("screen.myriad.g1."+groups[group]);
                text(name,b.x()+Math.max(0,(b.w()-graphics.getFontMetrics().stringWidth(name))/2),titleY,b.w(),GOLD);
                if(b.h()>=17)divider(b.x(),b.right(),b.bottom()-2,0xAA69B7A5);
            }
            int[] ranks={2,0,2,1,3,0,0,1};
            for(var skill:skills){
                int index=skill.get("index").getAsInt(),rank=ranks[index];var b=layout.card(index);
                if(!b.overlaps(view))continue;boolean locked=index==1||index==5||index==6;
                float rankScale=layout.rankScale();int rankHeight=(int)Math.ceil(9*rankScale);
                int size=Math.min(36,b.h()-rankHeight),iconX=b.x()+(b.w()-size)/2;
                if(index==2&&!unselected){
                    fill(iconX-1,b.y()-1,size+2,1,GOLD);fill(iconX-1,b.y()+size,size+2,1,GOLD);
                    fill(iconX-1,b.y(),1,size,GOLD);fill(iconX+size,b.y(),1,size,GOLD);
                }
                png("skill_icons/generated/"+icons.get(skill.get("iconAlias").getAsString()),new Box(iconX,b.y(),size,size));
                if(index<2||index>=4){fill(iconX,b.y(),(int)Math.ceil(7*rankScale),rankHeight,0xEE071116);scaledText(index%2==0?"A":"B",iconX+1,b.y(),8,GOLD,rankScale);}
                String rankText=locked?tr("screen.myriad.g1.locked"):rank+"/"+skill.get("maxRank").getAsInt();
                scaledText(rankText,b.x()+(b.w()-(int)Math.ceil(graphics.getFontMetrics().stringWidth(rankText)*rankScale))/2,b.bottom()-rankHeight,b.w(),locked?MUTED:JADE,rankScale);
            }
        }else if(page==1){
            var lines=wrap(tr("screen.myriad.g1.attribute_budget",4),view.w()-4);
            for(int i=0;i<lines.size();i++)text(lines.get(i),view.x()+2,view.y()+i*10-scroll,view.w()-4,GOLD);
            for(int i=0;i<5;i++){
                var b=layout.attribute(i,scroll);if(i==0&&!unselected)plate(b,GOLD,INK);
                png("attributes/"+ATTRIBUTE_ICONS[i],new Box(b.x()+(b.w()-26)/2,b.y()+2,26,26));
                String rankText=(i==0?2:i==1?2:0)+"/10";
                text(rankText,b.x()+(b.w()-graphics.getFontMetrics().stringWidth(rankText))/2,b.bottom()-10,b.w(),JADE);
            }
        }else{
            for(int i=0;i<SUPPLIES.length;i++){
                String id=SUPPLIES[i];var b=layout.supply(i,scroll);plate(b,0xFF3C716F,INK);
                png("supply_"+id,new Box(b.x()+4,b.y()+5,20,20));
                text(tr("item.myriad."+id),b.x()+29,b.y()+10,b.w()-108,GOLD);
                button(layout.supplyAction(i,scroll),tr("screen.myriad.g1.buy",prices.get(id)),false);
            }
        }
        graphics.setClip(saved);
        if(maximum>0){var t=layout.verticalTrack();var thumb=layout.verticalThumb(maximum,scroll);fill(t.x(),t.y(),t.w(),t.h(),0xAA071418);fill(thumb.x(),thumb.y(),thumb.w(),thumb.h(),0xBBAEBA8C);}
        divider(view.x(),view.right(),layout.footerDividerY(),0xAA69B7A5);
        if(page<2&&!unselected){
            String chosen=page==0?skills.get(2).get("name").getAsString():tr("screen.myriad.g1.attribute.strength");
            var label=layout.selectedLabel();float scaleText=layout.footerScale();
            var lines=wrap(tr("screen.myriad.g1.selected",chosen),(int)(label.w()/scaleText));
            for(int i=0;i<Math.min(2,lines.size());i++)scaledText(lines.get(i),label.x(),label.y()+2+(int)(i*10*scaleText),label.w(),GOLD,scaleText);
            button(layout.primary(),page==0?tr("screen.myriad.g1.rank_up"):tr("screen.myriad.g1.buy",260),true);
        }
        if(page==2)scaledText(tr("screen.myriad.g1.supply_hint"),view.x(),layout.footerDividerY()+8,view.w(),MUTED,layout.footerScale());
        else if(unselected)scaledText(tr(page==0?"screen.myriad.g1.icon_hint":"screen.myriad.g1.attribute_hint"),view.x(),layout.footerDividerY()+8,view.w(),MUTED,layout.footerScale());
        if(tooltip){
            var longest=skills.stream().max(Comparator.comparingInt(s->s.get("description").getAsString().length())).orElseThrow();
            var anchor=layout.card(longest.get("index").getAsInt()|1);
            var raw=new ArrayList<String>();raw.add(longest.get("name").getAsString());
            raw.add(tr("screen.myriad.g1.unlearned_rank",0,longest.get("maxRank").getAsInt()));
            raw.add("Lv.1 / "+longest.get("maxRank").getAsInt()+" · "+AbilityFormulaText.evaluate(longest.get("description").getAsString(),1,100));
            raw.add(tr("screen.myriad.g1.strength",100));
            int total=0,longestWidth=0;
            for(String value:raw){int measured=graphics.getFontMetrics().stringWidth(value);total+=measured;longestWidth=Math.max(longestWidth,measured);}
            int preferred=Math.clamp(Math.min(longestWidth+14,(int)Math.ceil(Math.sqrt(total*9*1.7))+14),48,220);
            int textWidth=SkillChoiceTooltipLayout.width(width,anchor,preferred)-14;
            var lines=new ArrayList<String>();for(String value:raw)lines.addAll(wrap(value,textWidth));
            var tip=SkillChoiceTooltipLayout.create(width,height,anchor,lines.size(),tooltipEnd?9999:0,preferred);var b=tip.box();plate(b,GOLD,0xFF081A21);
            int end=Math.min(lines.size(),tip.scroll()+tip.visibleLines());
            for(int i=tip.scroll();i<end;i++)text(lines.get(i),b.x()+7,b.y()+6+(i-tip.scroll())*10,b.w()-14,i==0?GOLD:JADE);
            if(tip.maximum()>0)text(tr("screen.myriad.g1.tooltip_scroll",tip.scroll()+1,end,lines.size()),b.x()+7,b.bottom()-12,b.w()-14,MUTED);
        }
        graphics.dispose();
        String name=hero+"-"+new String[]{"skills","attributes","supplies"}[page]+"-"+width+"x"+height+(unselected?"-unselected":"-selected")+(tooltip?tooltipEnd?"-tooltip-end":"-tooltip-start":"")+".png";
        Path file=output.resolve(name);ImageIO.write(image,"PNG",file.toFile());System.out.println(file);
    }
    public static void main(String[] args)throws Exception{
        Path project=Path.of(args[0]),output=Path.of(args[1]);Files.createDirectories(output);String hero=args.length>2?args[2]:"shuo";
        assets=project.resolve("src/main/resources/assets/myriad");language=JsonParser.parseString(Files.readString(assets.resolve("lang/zh_cn.json"))).getAsJsonObject();
        var candidates=new ArrayList<JsonObject>();
        for(String group:List.of("first_group","second_group","shuo","keheng","yike","rivet")){
            var document=JsonParser.parseString(Files.readString(project.resolve("src/main/resources/data/myriad/role_rework/"+group+".json"))).getAsJsonObject();
            for(var raw:document.getAsJsonArray("skills")){var skill=raw.getAsJsonObject();if(skill.get("role").getAsString().equals(hero))candidates.add(skill);}
        }
        candidates.sort(Comparator.comparingInt(s->s.get("index").getAsInt()));if(candidates.size()!=8)throw new IllegalArgumentException("Unknown hero: "+hero);
        var map=Pattern.compile("Map.entry\\(\"([^\"]+)\", \"([^\"]+)\"\\)").matcher(Files.readString(project.resolve("src/main/java/com/myriad/client/SkillIcon.java")));
        while(map.find())icons.put(map.group(1),map.group(2));
        var priceMap=Pattern.compile("case \\\"([^\\\"]+)\\\" -> new SupplyOffer\\(ModItems\\.[A-Z_]+\\.get\\(\\), (\\d+),").matcher(Files.readString(project.resolve("src/main/java/com/myriad/game/TacticalSupplySystem.java")));
        while(priceMap.find())prices.put(priceMap.group(1),Integer.parseInt(priceMap.group(2)));
        var background=ImageIO.read(assets.resolve("textures/gui/skill_choice/meridian_hall.png").toFile());
        for(int page=0;page<3;page++)for(int[] size:new int[][]{{640,360},{426,240},{320,180}})render(output,hero,candidates,background,size[0],size[1],page,true,false,false);
        render(output,hero,candidates,background,320,180,0,false,false,false);
        render(output,hero,candidates,background,426,240,0,true,true,false);
        render(output,hero,candidates,background,320,180,0,true,true,false);
        render(output,hero,candidates,background,320,180,0,true,true,true);
    }
}
