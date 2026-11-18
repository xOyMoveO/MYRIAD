package com.myriad.client;

/** One-shot client-thread mailbox. Submission never runs inside ChatScreen's submit stack. */
public final class DeferredScreenRequest {
    private Object session,world;
    private Runnable pending;
    public void submit(Object session,Object world,Runnable action){this.session=session;this.world=world;pending=action;}
    public void clear(){pending=null;session=null;world=null;}
    public void tick(Object session,Object world,boolean chatOpen,boolean otherScreenOpen){
        if(pending==null)return;
        if(session==null||world==null||this.session!=session||this.world!=world||otherScreenOpen){clear();return;}
        if(chatOpen)return;
        Runnable action=pending;clear();action.run();
    }
}
