package com.myriad.game;

import com.myriad.client.gui.SkillChoiceLayout;
import com.myriad.character.rework.FirstGroupSkills;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.List;
import javax.imageio.ImageIO;

/** Pure UI geometry/input contract and real packaged artwork; not client screenshot acceptance. */
final class SkillChoiceUiChecks {
    static void verify(GameTestHelper h){
        int states=0;
        for(int width:new int[]{320,384,480,576,640,960})for(int height:new int[]{180,240,270,344,480}){
            var base=SkillChoiceLayout.create(width,height,0,0);
            h.assertTrue(base.panel().x()>=0&&base.panel().right()<=width&&base.panel().bottom()<=height,"Panel remains inside the scaled window");
            for(int sx:new int[]{-999,9999})for(int sy:new int[]{-999,9999}){
                var layout=SkillChoiceLayout.create(width,height,sx,sy);var view=layout.viewport();
                h.assertTrue(layout.scrollX()>=0&&layout.scrollX()<=layout.maxX()&&layout.scrollY()>=0&&layout.scrollY()<=layout.maxY(),"Scrolling and resizing clamp both axes");
                for(int index=0;index<8;index++){
                    var card=layout.card(index);var action=layout.action(index);
                    h.assertTrue(card.x()==layout.column(index/2).x(),"Every category is a column, rather than a vertical category row");
                    h.assertTrue(action.x()>card.x()&&action.right()<card.right()&&action.y()>card.y()&&action.bottom()<card.bottom(),"Action remains fully inside its card");
                    if(index%2==1)h.assertTrue(layout.card(index-1).bottom()<card.y(),"The two alternatives have a clear comparison gap");
                    if(index>=2)h.assertTrue(layout.card(index-2).right()<card.x(),"Four groups are ordered left to right without overlap");
                    h.assertTrue(!layout.canHit(action,action.x()+1,view.y()-1)&&!layout.canHit(action,view.right(),action.y()+1),"Off-scissor buttons can never receive clicks");
                }
                var ht=layout.horizontalTrack();var hh=layout.horizontalThumb();var vt=layout.verticalTrack();var vh=layout.verticalThumb();
                h.assertTrue(hh.x()>=ht.x()&&hh.right()<=ht.right()&&vh.y()>=vt.y()&&vh.bottom()<=vt.bottom(),"Scroll handles stay inside their tracks");
                h.assertTrue(layout.scrollToHorizontal(-999)==0&&layout.scrollToHorizontal(99999)==layout.maxX(),"Horizontal dragging reaches both ends");
                h.assertTrue(layout.scrollToVertical(-999)==0&&layout.scrollToVertical(99999)==layout.maxY(),"Vertical dragging reaches both ends");
                states++;
            }
            // Every upgrade action remains reachable, including the rightmost lower card on small GUIs.
            for(int index=0;index<8;index++){
                var action=base.action(index);var view=base.viewport();
                var layout=SkillChoiceLayout.create(width,height,action.x()+action.w()/2-view.x()-view.w()/2,action.y()+action.h()/2-view.y()-view.h()/2);
                var moved=layout.action(index);
                h.assertTrue(layout.canHit(moved,moved.x()+moved.w()/2,moved.y()+moved.h()/2),"All eight buttons remain reachable at every tested GUI size");
            }
        }
        for(String role:FirstGroupSkills.ROLES){
            var list=FirstGroupSkills.candidates(role);
            h.assertTrue(list.size()==8&&FirstGroupSkills.opposite(list.get(2).index())<0&&FirstGroupSkills.opposite(list.get(3).index())<0,
                    "The regular column must not falsely claim the two normal skills are mutually exclusive");
        }
        try(var input=SkillChoiceUiChecks.class.getResourceAsStream("/assets/myriad/textures/gui/skill_choice/meridian_hall.png")){
            h.assertTrue(input!=null,"New illustrated panel is in the mod, not only in a local design folder");
            var image=ImageIO.read(input);
            h.assertTrue(image!=null&&image.getWidth()==1774&&image.getHeight()==887,"Accepted 2:1 source artwork decodes at its native size");
            h.assertTrue(image.getRGB(887,443)!=image.getRGB(10,55),"Source retains quiet jade interior and a distinct ornament frame");
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        System.out.println("[MYRIAD-SKILL-UI] PASS: "+states+" responsive states, 4 horizontal groups / 8 reachable cards, scissor-safe input, scrollbar endpoints, real packaged jade panel");
    }
}
