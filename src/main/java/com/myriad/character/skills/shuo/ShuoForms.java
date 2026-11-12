package com.myriad.character.skills.shuo;
/** Presentation metadata only; concrete skill files own all stance-dependent mechanics. */
public final class ShuoForms {
    private ShuoForms(){}
    public static String name(String id,boolean drawn,boolean recast){
        return switch(id){
            case "shuo_nadao"->drawn?"回风·纳刀":"潜步·藏锋";
            case "shuo_qiaofan"->recast?"鞘返·反击":drawn?"流刃":"鞘返";
            case "shuo_yishan"->drawn?"追风":"一闪";
            case "shuo_duanliu"->drawn?"回澜双斩":"断流";
            case "shuo_wusheng"->drawn?"断空":"无声一线";
            case "shuo_canyue"->recast?"残月三式·续":drawn?"回月·终斩":"残月三式";
            default->"";
        };
    }
}
