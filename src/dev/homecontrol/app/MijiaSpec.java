package dev.homecontrol.app;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Public MIoT schema cache; no account/session data is written here. */
final class MijiaSpec {
    private static final long DAY=86400000L;
    static synchronized JSONObject load(Context c,String model,String hint)throws Exception{
        if(model.isEmpty())throw new IOException("此设备未提供控制模型");File directory=new File(c.getCacheDir(),"mijia-spec");directory.mkdirs();File target=new File(directory,DeviceIdentity.mijia("schema","cn",model).substring(6)+".json");JSONObject cached=read(target);
        if(cached!=null&&System.currentTimeMillis()-target.lastModified()<30*DAY)return cached;
        String type=hint;if(!type.startsWith("urn:miot-spec-v2:device:")){
            File index=new File(directory,"index.json");JSONObject catalog=read(index);if(catalog==null||System.currentTimeMillis()-index.lastModified()>7*DAY){try{catalog=download("/miot-spec-v2/instances?status=all",16777216);write(index,catalog);}catch(Exception e){if(catalog==null){if(cached!=null)return cached;throw e;}}}
            JSONObject best=null;JSONArray instances=catalog.optJSONArray("instances");if(instances!=null)for(int i=0;i<instances.length();i++){JSONObject d=instances.optJSONObject(i);if(d==null||!model.equals(d.optString("model")))continue;if(best==null||"released".equals(d.optString("status"))&&!"released".equals(best.optString("status"))||d.optString("status").equals(best.optString("status"))&&d.optInt("version")>best.optInt("version"))best=d;}
            if(best==null){if(cached!=null)return cached;throw new IOException("此设备暂未提供通用云端控制模型");}type=best.optString("type");
        }
        try{JSONObject spec=download("/miot-spec-v2/instance?"+MijiaWire.form(Collections.singletonMap("type",type)),2097152);if(!type.equals(spec.optString("type"))||spec.optJSONArray("services")==null)throw new IOException("设备能力模型不兼容");
            try{JSONObject translations=download("/instance/v2/multiLanguage?"+MijiaWire.form(Collections.singletonMap("urn",type)),2097152).optJSONObject("data");if(translations!=null)spec.put("_translations",translations);}catch(Exception ignored){}
            write(target,spec);return spec;
        }catch(Exception e){if(cached!=null)return cached;throw e;}
    }
    private static JSONObject download(String path,int limit)throws Exception{Exception last=null;for(String host:new String[]{"https://miot-spec.org","https://spec.miot-spec.com"})try{MijiaHttp.Reply r=new MijiaHttp().request(host+path,null,Collections.emptyMap(),null,MijiaWire::specUrl,limit,20000);if(r.code!=200)throw new IOException("设备能力服务暂不可用");return new JSONObject(r.body);}catch(Exception e){last=e;}throw new IOException("设备能力读取失败，请稍后刷新");}
    private static JSONObject read(File f){try{return new JSONObject(new String(new AtomicFile(f).readFully(),StandardCharsets.UTF_8));}catch(Exception e){return null;}}
    private static void write(File f,JSONObject data)throws Exception{AtomicFile file=new AtomicFile(f);FileOutputStream out=null;try{out=file.startWrite();out.write(data.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}}
    private MijiaSpec(){}
}
