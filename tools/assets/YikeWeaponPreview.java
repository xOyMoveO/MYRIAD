import com.myriad.character.skills.yike.YikeWeaponAnimation;
import com.myriad.character.skills.yike.YikeWeaponAnimation.Controller;
import com.myriad.character.skills.yike.YikeWeaponAnimation.Snapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Exports the production animation controller; no second set of preview curves. */
public final class YikeWeaponPreview {
    public static void main(String[] args) throws Exception {
        var output=Path.of(args[0]);Files.createDirectories(output.getParent());
        var csv=new StringBuilder("scenario,frame,time,mode,windup,progress,petal0,petal1,petal2,ringSlide,latch,ringTwist,coreReady,aimBlend,idleMobile,recoil,sway1,sway2\n");
        for(String scenario:new String[]{"fixed","mobile","cancel","shot"}){
            Controller controller=new Controller();int lastTick=-1;Snapshot sample=null;
            if(scenario.equals("shot")){controller.accept(new Snapshot(1,-1,0,-2),-2);controller.accept(new Snapshot(1,-1,0,-1),-1);}
            for(int frame=0;frame<=70;frame++){
                double time=frame*.4;
                for(int tick=lastTick+1;tick<=Math.floor(time+1E-8);tick++){
                    sample=snapshot(scenario,tick);controller.accept(sample,tick);
                    if(scenario.equals("shot")&&tick==6)controller.fire(1006,tick);
                    lastTick=tick;
                }
                var pose=controller.pose(time);
                csv.append(String.format(Locale.ROOT,"%s,%d,%.6f,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                        scenario,frame,time,sample.mode(),sample.windup(),sample.progress(),
                        pose.petal0(),pose.petal1(),pose.petal2(),pose.ringSlide(),pose.latch(),pose.ringTwist(),
                        pose.coreReady(),pose.aimBlend(),pose.idleMobile(),pose.recoil(),pose.petalSway(1,time),pose.petalSway(2,time)));
            }
        }
        Files.writeString(output,csv);System.out.println("Exported production Yike poses: "+output);
    }
    private static Snapshot snapshot(String scene,int tick){
        if(scene.equals("fixed"))return tick<9?new Snapshot(0,4,tick/9.0,tick):new Snapshot(tick<18?1:0,-1,0,tick);
        if(scene.equals("mobile"))return tick<7?new Snapshot(0,5,tick/7.0,tick):new Snapshot(tick<18?2:0,-1,0,tick);
        if(scene.equals("cancel")){
            double[] progress={0,.12,.24,.32,.32,.32,.38,.44};
            return tick<progress.length?new Snapshot(0,4,progress[tick],tick):new Snapshot(0,-1,0,tick);
        }
        return new Snapshot(tick<18?1:0,tick==4||tick==5?2:-1,tick==4?.2:tick==5?.6:0,tick);
    }
}
