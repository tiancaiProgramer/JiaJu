package dev.homecontrol.app;

import android.content.Context;
import android.media.*;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.nio.ByteBuffer;

/** Remux the received encoded video; no screen recording service or background camera. */
final class CameraRecording {
    private final CameraCaptures.Pending pending;private final ParcelFileDescriptor file;private final MediaMuxer muxer;private final int track,codec;
    private boolean closed,started;private long base=-1,last=-1;private int frames;
    CameraRecording(Context context,String id,MediaFormat format)throws Exception{String mime=format.getString(MediaFormat.KEY_MIME);if(!"video/hevc".equals(mime)&&!"video/avc".equals(mime))throw new IOException("当前视频编码不支持录制");codec="video/hevc".equals(mime)?5:4;pending=new CameraCaptures.Pending(context,id,"video");ParcelFileDescriptor descriptor=null;MediaMuxer writer=null;try{descriptor=context.getContentResolver().openFileDescriptor(pending.uri,"rw");if(descriptor==null)throw new IOException("无法创建录像");writer=new MediaMuxer(descriptor.getFileDescriptor(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);track=writer.addTrack(format);writer.start();file=descriptor;muxer=writer;}catch(Exception e){if(writer!=null)writer.release();if(descriptor!=null)descriptor.close();pending.abort();throw e;}}
    synchronized void accept(int type,long timestamp,byte[] data)throws Exception{if(closed)return;if(type!=codec)throw new IOException("视频编码已改变，录像已停止");boolean key=keyFrame(type,data);if(!started&&!key)return;if(!started){started=true;base=timestamp;}long pts=Math.max(last+1,Math.max(0,timestamp-base)*1000);last=pts;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();info.set(0,data.length,pts,key?MediaCodec.BUFFER_FLAG_KEY_FRAME:0);muxer.writeSampleData(track,ByteBuffer.wrap(data),info);frames++;}
    synchronized String stop()throws Exception{if(closed)return null;closed=true;Exception failure=null;try{if(frames>0)muxer.stop();}catch(Exception e){failure=e;}finally{muxer.release();file.close();}if(failure!=null||frames==0){pending.abort();throw new IOException(frames==0?"尚未收到关键帧，未保存空录像":"录像保存失败");}pending.finish();return pending.name;}
    synchronized void abort(){if(closed)return;closed=true;try{muxer.release();}catch(Exception ignored){}try{file.close();}catch(Exception ignored){}pending.abort();}
    static boolean keyFrame(int type,byte[] data){for(byte[] nal:CameraDecoder.nalUnits(data)){int offset=nal[2]==1?3:4;if(offset>=nal.length)continue;int n=type==4?nal[offset]&31:(nal[offset]>>1)&63;if(type==4&&n==5||type==5&&n>=16&&n<=23)return true;}return false;}
}
