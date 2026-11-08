package dev.homecontrol.app;

import android.content.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Only explicit capture actions create media. Pending/empty files are removed. */
final class CameraCaptures {
    static final class Pending {
        final Context context;final String id,name,kind;final Uri uri;boolean done;
        Pending(Context c,String id,String kind)throws Exception{context=c.getApplicationContext();this.id=id;this.kind=kind;name="JiaJu_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.ROOT).format(new Date())+("video".equals(kind)?".mp4":".png");ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);values.put(MediaStore.MediaColumns.MIME_TYPE,"video".equals(kind)?"video/mp4":"image/png");values.put(MediaStore.MediaColumns.RELATIVE_PATH,("video".equals(kind)?"Movies":"Pictures")+"/JiaJu");values.put(MediaStore.MediaColumns.IS_PENDING,1);uri=c.getContentResolver().insert("video".equals(kind)?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("无法创建保存文件");}
        synchronized void finish()throws Exception{if(done)return;ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.IS_PENDING,0);context.getContentResolver().update(uri,values,null,null);remember(context,id,new JSONObject().put("uri",uri.toString()).put("name",name).put("kind",kind).put("created",System.currentTimeMillis()));done=true;}
        synchronized void abort(){if(done)return;done=true;try{context.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}}
    }
    static void picture(Context context,String id,Bitmap image)throws Exception{Pending pending=new Pending(context,id,"image");try(OutputStream out=context.getContentResolver().openOutputStream(pending.uri)){if(out==null||!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("截图保存失败");out.flush();pending.finish();}catch(Exception e){pending.abort();throw e;}}
    private static synchronized void remember(Context c,String id,JSONObject record)throws Exception{JSONArray old=list(c,id),next=new JSONArray().put(record);for(int i=0;i<Math.min(199,old.length());i++)next.put(old.get(i));DeviceStore.putPreference(c,"camera_captures:"+id,next.toString());}
    static JSONArray list(Context c,String id){try{return new JSONArray(DeviceStore.preference(c,"camera_captures:"+id,"[]"));}catch(Exception e){return new JSONArray();}}
    static void open(android.app.Activity owner,JSONObject item){try{Uri uri=Uri.parse(item.getString("uri"));Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"video".equals(item.optString("kind"))?"video/mp4":"image/png").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);owner.startActivity(intent);}catch(Exception e){android.widget.Toast.makeText(owner,"未找到可打开该文件的应用，或文件已移除",android.widget.Toast.LENGTH_LONG).show();}}
}
