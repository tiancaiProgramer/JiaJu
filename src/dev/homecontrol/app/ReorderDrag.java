package dev.homecontrol.app;

import android.content.ClipData;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Bundle;
import java.util.*;

/** Local-only native drag shadow; changes commit on drop, never on hover or cancellation. */
final class ReorderDrag {
    interface Commit {void move(String source,String target);}
    private final Object owner=new Object();private final AppUi ui;private final Commit commit;private final Runnable start,end;
    private boolean active;
    private static final class Item {final Object owner;final String id;final View source;String target;Item(Object o,String id,View v){owner=o;this.id=id;source=v;}}
    ReorderDrag(AppUi ui,Commit commit,Runnable start,Runnable end){this.ui=ui;this.commit=commit;this.start=start;this.end=end;}
    boolean active(){return active;}
    boolean begin(View source,String id){if(active)return false;Item item=new Item(owner,id,source);boolean accepted=source.startDragAndDrop(ClipData.newPlainText("家聚设备排序",id),new View.DragShadowBuilder(source),item,0);if(accepted){active=true;source.setAlpha(.35f);start.run();}return accepted;}
    void target(View view,String id){view.setOnDragListener((v,event)->{Object local=event.getLocalState();if(!(local instanceof Item)||((Item)local).owner!=owner)return false;Item item=(Item)local;
        switch(event.getAction()){
            case DragEvent.ACTION_DRAG_STARTED:return true;
            case DragEvent.ACTION_DRAG_ENTERED:if(!id.equals(item.id))v.setAlpha(.65f);return true;
            case DragEvent.ACTION_DRAG_EXITED:if(v!=item.source)v.setAlpha(1);return true;
            case DragEvent.ACTION_DROP:if(!id.equals(item.id))item.target=id;return true;
            case DragEvent.ACTION_DRAG_ENDED:v.setAlpha(1);if(active){active=false;item.source.setAlpha(1);if(event.getResult()&&item.target!=null)commit.move(item.id,item.target);end.run();}return true;
            default:return true;
        }});
    }
    void handle(View handle,View row,String id){final float[] down=new float[2];final boolean[] moving={false};handle.setContentDescription("拖动排序"+row.getContentDescription());handle.setFocusable(true);handle.setClickable(true);
        handle.setOnTouchListener((v,e)->{switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:down[0]=e.getX();down[1]=e.getY();moving[0]=false;v.getParent().requestDisallowInterceptTouchEvent(true);return true;
            case MotionEvent.ACTION_MOVE:if(!moving[0]&&Math.hypot(e.getX()-down[0],e.getY()-down[1])>ViewConfiguration.get(v.getContext()).getScaledTouchSlop())moving[0]=begin(row,id);return true;
            case MotionEvent.ACTION_UP:v.getParent().requestDisallowInterceptTouchEvent(false);if(!moving[0])v.performClick();return true;
            case MotionEvent.ACTION_CANCEL:v.getParent().requestDisallowInterceptTouchEvent(false);return true;
            default:return false;}});
        handle.setOnLongClickListener(v->begin(row,id));handle.setOnClickListener(v->android.widget.Toast.makeText(v.getContext(),"拖住图标移动到目标位置",android.widget.Toast.LENGTH_SHORT).show());
    }
    void accessible(View view,String id,List<String> order){view.setAccessibilityDelegate(new View.AccessibilityDelegate(){
        @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);int index=order.indexOf(id);if(index>0)info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,"前移"));if(index>=0&&index+1<order.size())info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,"后移"));}
        @Override public boolean performAccessibilityAction(View host,int action,Bundle args){int index=order.indexOf(id),to=action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD?index-1:action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?index+1:-1;if(index>=0&&to>=0&&to<order.size()){commit.move(id,order.get(to));return true;}return super.performAccessibilityAction(host,action,args);}
    });}
}
