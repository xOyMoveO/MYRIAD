package com.myriad.client.gui;

/** Selection and double-click contract, independent of client rendering and packets. */
public final class SkillChoiceInteraction {
    private String selected="";
    private boolean doubleConsumed;
    public String selected(){return selected;}
    public void reset(){selected="";doubleConsumed=false;}
    /** A first click only selects. A double click on that same item may activate once. */
    public boolean select(String id,boolean twice,boolean eligible){
        boolean same=selected.equals(id);
        if(!twice||!same)doubleConsumed=false;
        selected=id;
        if(twice&&same&&!doubleConsumed){doubleConsumed=true;return eligible;}
        return false;
    }
    /** The second event in a double-click on the explicit button must not buy twice. */
    public boolean confirm(String id,boolean twice,boolean eligible){return !twice&&eligible&&selected.equals(id);}
}
