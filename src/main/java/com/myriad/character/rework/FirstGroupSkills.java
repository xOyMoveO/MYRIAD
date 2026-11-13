package com.myriad.character.rework;

import com.google.gson.JsonParser;
import com.myriad.character.CharacterType;
import com.myriad.character.Skill;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilitySkill.Definition;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Batch binding and rank validation only; mechanics live in individual role/skill classes. */
public final class FirstGroupSkills {
    private FirstGroupSkills() {}
    // Historical class name is retained for saved/network entry points; registration spans both migrated batches.
    public static final Set<String> SECOND_GROUP=Set.of("lingyuan","hanze","qingluo","chixiao","yunsheng");
    public static final Set<String> ROLES=Set.of("yingxiao","tianxing","fenjue","xuanchen","sulan","lingyuan","hanze","qingluo","chixiao","yunsheng","shuo","keheng");
    private static final Map<String,List<AbilitySkill>> BY_ROLE=new LinkedHashMap<>();
    private static final Map<String,AbilitySkill> BY_ID=new LinkedHashMap<>();
    static {
        try { for(String resource:List.of("first_group","second_group","shuo","keheng")) {
          try(var stream=FirstGroupSkills.class.getResourceAsStream("/data/myriad/role_rework/"+resource+".json")) {
            if(stream==null)throw new IllegalStateException("Missing first-group skill definitions");
            var doc=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var raw:doc.getAsJsonArray("skills")) {
                var d=raw.getAsJsonObject();
                int[] cooldowns=d.getAsJsonArray("cooldowns").asList().stream().mapToInt(e->e.getAsInt()).toArray();
                String role=d.get("role").getAsString(),id=d.get("id").getAsString();int index=d.get("index").getAsInt();
                var definition=new Definition(id,role,index,d.get("maxRank").getAsInt(),d.get("mana").getAsFloat(),cooldowns,
                        d.get("name").getAsString(),d.get("description").getAsString(),d.get("iconAlias").getAsString());
                AbilitySkill node=switch(role){
                    case "keheng"->com.myriad.character.skills.keheng.KehengRework.bind(definition);
                    case "shuo"->com.myriad.character.skills.shuo.ShuoRework.bind(definition);
                    case "yingxiao"->com.myriad.character.skills.yingxiao.rework.YingxiaoRework.bind(definition);
                    case "tianxing"->com.myriad.character.skills.tianxing.rework.TianxingRework.bind(definition);
                    case "fenjue"->com.myriad.character.skills.fenjue.rework.FenjueRework.bind(definition);
                    case "xuanchen"->com.myriad.character.skills.xuanchen.rework.XuanchenRework.bind(definition);
                    case "sulan"->com.myriad.character.skills.sulan.rework.SulanRework.bind(definition);
                    case "lingyuan"->com.myriad.character.skills.lingyuan.rework.LingyuanRework.bind(definition);
                    case "hanze"->com.myriad.character.skills.hanze.rework.HanzeRework.bind(definition);
                    case "qingluo"->com.myriad.character.skills.qingluo.rework.QingluoRework.bind(definition);
                    case "chixiao"->com.myriad.character.skills.chixiao.rework.ChixiaoRework.bind(definition);
                    case "yunsheng"->com.myriad.character.skills.yunsheng.rework.YunshengRework.bind(definition);
                    default->throw new IllegalArgumentException("Unknown role "+role);
                };
                if(!ROLES.contains(node.role())||node.index()<0||node.index()>7||BY_ID.putIfAbsent(node.getId(),node)!=null)
                    throw new IllegalArgumentException("Invalid first-group binding: "+node.getId());
                BY_ROLE.computeIfAbsent(node.role(),key->new ArrayList<>()).add(node);
            }
          }
        }
            for(String role:ROLES){
                var nodes=BY_ROLE.get(role);if(nodes==null||nodes.size()!=8)throw new IllegalArgumentException("Incomplete graph "+role);
                nodes.sort(Comparator.comparingInt(AbilitySkill::index));
                for(int i=0;i<8;i++)if(nodes.get(i).index()!=i||nodes.get(i).maxRank()!=(i<4?5:i<6?7:3))
                    throw new IllegalArgumentException("Invalid category/cap "+role);
                BY_ROLE.put(role,List.copyOf(nodes));
            }
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    public static boolean active(String id){return id!=null&&ROLES.contains(id);}
    public static List<AbilitySkill> candidates(String role){return BY_ROLE.getOrDefault(role,List.of());}
    public static AbilitySkill byId(String id){return BY_ID.get(id);}
    public static AbilitySkill node(String role,int index){var list=candidates(role);return index>=0&&index<list.size()?list.get(index):null;}
    public static int opposite(int index){return switch(index){case 0->1;case 1->0;case 4->5;case 5->4;case 6->7;case 7->6;default->-1;};}
    public static AbilitySkill selected(String role,int first,Map<String,Integer> ranks){
        AbilitySkill a=node(role,first),b=node(role,first+1);
        return b!=null&&ranks.getOrDefault(b.getId(),0)>0?b:a;
    }
    public static Skill resolve(CharacterType role,int slot,Map<String,Integer> ranks){
        if(role==null)return null;
        if(!active(role.getId()))return role.skill(slot);
        return switch(slot){case 0->node(role.getId(),2);case 1->node(role.getId(),3);
            case 2->selected(role.getId(),4,ranks);case 3->selected(role.getId(),6,ranks);default->null;};
    }
    public static String learnError(String role,Map<String,Integer> ranks,int points,int level,String id){
        AbilitySkill n=byId(id);if(n==null||!n.role().equals(role))return "message.myriad.g1.invalid_skill";
        if(points<=0)return "message.myriad.no_skill_points";
        int current=ranks.getOrDefault(id,0);
        if(current>=n.maxRank())return "message.myriad.skill_max";
        int other=opposite(n.index());
        if(other>=0&&ranks.getOrDefault(node(role,other).getId(),0)>0)return "message.myriad.g1.branch_locked";
        // One point per level; ultimates keep the established 4/8/12 unlock cadence.
        int gate=n.isUltimate()?new int[]{4,8,12}[Math.min(current,2)]:current+1;
        if(level<gate)return "message.myriad.g1.level_gate";
        int spent=candidates(role).stream().mapToInt(s->Math.max(0,ranks.getOrDefault(s.getId(),0))).sum();
        return spent>=Math.min(20,Math.max(1,level))?"message.myriad.no_skill_points":null;
    }
}
