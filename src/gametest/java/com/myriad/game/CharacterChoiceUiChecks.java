package com.myriad.game;
import com.myriad.client.gui.CharacterChoiceLayout;
import com.myriad.client.gui.SkillChoiceLayout.Box;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.*;

/** Real panel hit geometry and synced availability rules, not graphical-client acceptance. */
final class CharacterChoiceUiChecks {
    private static boolean inside(Box a,Box b){return a.x()>=b.x()&&a.y()>=b.y()&&a.right()<=b.right()&&a.bottom()<=b.bottom();}
    static void verify(GameTestHelper h){
        int samples=0;
        for(int[] screen:new int[][]{{256,144},{320,180},{427,240},{576,324},{640,360},{960,540},{1920,1080}}){
            int count=com.myriad.character.CharacterType.playableValues().length;
            var first=CharacterChoiceLayout.create(screen[0],screen[1],count,0);var covered=new HashSet<Integer>();
            for(int row=0;row<=first.maxScroll();row++){
                var l=CharacterChoiceLayout.create(screen[0],screen[1],count,row);
                h.assertTrue(inside(l.panel(),new Box(0,0,screen[0],screen[1]))&&inside(l.grid(),l.panel())&&inside(l.details(),l.panel()),"Panel and both columns fit the real GUI viewport");
                h.assertTrue(!l.grid().overlaps(l.details()),"Role cards and skill details have separate hit regions");
                for(var b:List.of(l.previous(),l.next(),l.guide(),l.confirm(),l.close()))h.assertTrue(inside(b,l.panel())&&!b.overlaps(l.grid())&&!b.overlaps(l.details()),"Live controls remain visible, outside card/detail content");
                h.assertTrue(!l.guide().overlaps(l.confirm()),"Guide and confirm buttons never overlap");
                for(int i=0;i<count;i++)if(l.visible(i)){
                    var b=l.card(i);h.assertTrue(inside(b,l.grid()),"Every displayed role card is wholly inside its grid");
                    h.assertTrue(b.contains(b.x()+b.w()/2.0,b.y()+b.h()/2.0),"Card centre is a clickable hit target");covered.add(i);
                    for(int j=i+1;j<count;j++)if(l.visible(j))h.assertTrue(!b.overlaps(l.card(j)),"Role cards never overlap");
                }samples++;
            }
            h.assertTrue(covered.size()==count,"Scrolling reaches all fourteen roles at every tested GUI size");
            h.assertTrue(CharacterChoiceLayout.create(screen[0],screen[1],count,-999).scrollRow()==0&&CharacterChoiceLayout.create(screen[0],screen[1],count,999).scrollRow()==first.maxScroll(),"Scroll remains clamped");
        }
        h.assertTrue(CharacterChoiceLayout.available("shuo","shuo",Map.of("shuo",true))&&!CharacterChoiceLayout.available("shuo","rivet",Map.of("shuo",true))&&CharacterChoiceLayout.available("yike","rivet",Map.of())&&!CharacterChoiceLayout.available(null,"",Map.of())&&!CharacterChoiceLayout.available("","",Map.of()),"Current role remains confirmable; another player's role and empty selections do not");
        h.assertTrue(!CharacterChoiceLayout.canConfirm(false,true)
                && !CharacterChoiceLayout.canConfirm(true,false) && CharacterChoiceLayout.canConfirm(true,true),
                "The preview remains available while disabled, but confirmation requires enabled gameplay and an available role");
        System.out.println("[MYRIAD-CHOOSE-UI] PASS: "+samples+" real card-layout/scroll states at seven GUI sizes, complete roster coverage, separate hit areas and current/occupied availability; no live graphical acceptance");
    }
}
