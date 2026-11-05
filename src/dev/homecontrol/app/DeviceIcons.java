package dev.homecontrol.app;
import android.content.Context;
import android.graphics.*;
import org.json.JSONObject;
final class DeviceIcons {
    static Bitmap make(Context c,JSONObject device,int color){Bitmap bitmap=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);Paint p=new Paint(3);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setColor(color);
        String model=device.optString("model").toLowerCase(java.util.Locale.ROOT);
        if("haier_hood".equals(device.optString("deviceType"))){canvas.drawLine(32,12,48,12,p);canvas.drawLine(32,12,32,30,p);canvas.drawLine(48,12,48,30,p);canvas.drawLine(32,30,12,48,p);canvas.drawLine(48,30,68,48,p);canvas.drawRoundRect(10,48,70,63,3,3,p);canvas.drawLine(21,70,28,70,p);canvas.drawLine(36,70,43,70,p);canvas.drawLine(51,70,58,70,p);}
        else if("fusion_hidden".equals(device.optString("deviceType"))){Path path=new Path();path.moveTo(10,25);path.lineTo(10,65);path.lineTo(70,65);path.lineTo(70,25);path.lineTo(38,25);path.lineTo(30,16);path.lineTo(10,16);path.close();canvas.drawPath(path,p);}
        else if(model.contains(".light.")){canvas.drawCircle(40,30,21,p);canvas.drawLine(29,49,29,62,p);canvas.drawLine(51,49,51,62,p);canvas.drawLine(29,62,51,62,p);canvas.drawLine(33,70,47,70,p);}
        else if(model.contains(".tv.")){canvas.drawRoundRect(8,14,72,57,4,4,p);canvas.drawLine(40,57,40,68,p);canvas.drawLine(25,68,55,68,p);}
        else if(model.contains(".plug.")||model.contains(".outlet.")){canvas.drawRoundRect(15,9,65,71,9,9,p);canvas.drawLine(30,28,30,39,p);canvas.drawLine(50,28,50,39,p);canvas.drawLine(40,49,40,59,p);}
        else if(model.contains(".lock.")){canvas.drawRoundRect(15,34,65,70,6,6,p);canvas.drawArc(24,8,56,56,180,180,false,p);canvas.drawCircle(40,49,4,p);canvas.drawLine(40,53,40,60,p);}
        else{canvas.drawRoundRect(13,15,67,69,13,13,p);canvas.drawCircle(40,39,10,p);}return bitmap;
    }
}
