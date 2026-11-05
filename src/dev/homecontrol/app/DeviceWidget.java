package dev.homecontrol.app;
import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;
import org.json.*;
import java.util.*;
public final class DeviceWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){for(int id:ids)manager.updateAppWidget(id,render(c));}
    static void updateAll(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);for(int id:m.getAppWidgetIds(new ComponentName(c,DeviceWidget.class)))m.updateAppWidget(id,render(c));MainActivity.notifyChanged();}
    private static RemoteViews render(Context c){RemoteViews view=new RemoteViews(c.getPackageName(),R.layout.device_widget);view.setOnClickPendingIntent(R.id.heading,PendingIntent.getActivity(c,1,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));List<JSONObject> selected=new DisplayPreferences(c).selected(DeviceStore.devices(c));int[] slots={R.id.d1,R.id.d2,R.id.d3,R.id.d4};
        for(int i=0;i<slots.length;i++){if(i>=selected.size()){view.setViewVisibility(slots[i],View.INVISIBLE);continue;}JSONObject d=selected.get(i);String id=d.optString("id");view.setViewVisibility(slots[i],View.VISIBLE);view.setTextViewText(slots[i],d.optString("title")+"\n"+d.optString("subtitle"));Intent target=DevicePresentation.HIDDEN.equals(id)?new Intent(c,MainActivity.class).putExtra("showHidden",true):CloudDevices.panel(c,id);target.setData(Uri.parse("homecontrol://device/"+Uri.encode(id)));view.setOnClickPendingIntent(slots[i],PendingIntent.getActivity(c,id.hashCode(),target,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));}return view;}
}
