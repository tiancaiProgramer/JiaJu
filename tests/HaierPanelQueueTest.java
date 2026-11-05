package dev.homecontrol.app;
public final class HaierPanelQueueTest {
    static int count;static void check(boolean yes){count++;if(!yes)throw new AssertionError("Panel queue check "+count);}
    public static void main(String[] args){
        HaierPanelQueue q=new HaierPanelQueue();check(q.next()==null);check("false".equals(q.desired("onOffStatus","false")));
        q.offer("windSpeedLevel","1");HaierPanelQueue.Command active=q.next();check(active!=null&&"1".equals(active.value));check(q.next()==null);
        q.offer("windSpeedLevel","3");q.offer("windSpeedLevel","4");q.offer("lightingStatus","true");check("4".equals(q.desired("windSpeedLevel","0")));
        q.completed(active);HaierPanelQueue.Command last=q.next();check(last!=null&&"4".equals(last.value));q.completed(last);check("lightingStatus".equals(q.next().key));
        q=new HaierPanelQueue();q.offer("onOffStatus","true");active=q.next();check("true".equals(q.desired("onOffStatus","false")));q.offer("onOffStatus","false");check("false".equals(q.desired("onOffStatus","true")));q.completed(active);check("false".equals(q.next().value));
        q=new HaierPanelQueue();q.offer("onOffStatus","true");active=q.next();q.offer("windSpeedLevel","3");q.clearWaiting();check(q.next()==null);check("true".equals(q.desired("onOffStatus","false")));q.completed(active);check(q.next()==null);check("false".equals(q.desired("onOffStatus","false")));
        q=new HaierPanelQueue();q.offer("windSpeedLevel","1");active=q.next();q.offer("windSpeedLevel","4");q.offer("windSpeedLevel","1");q.completed(active);check(q.next()==null);
        System.out.println(count+" stable panel command checks passed");
    }
}
