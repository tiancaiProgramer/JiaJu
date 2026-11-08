package dev.homecontrol.app;

import android.media.*;
import android.os.*;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.*;

/** Streaming decoder for Annex-B H.264/H.265. Frames are never written to storage. */
final class CameraDecoder implements AutoCloseable {
    private final Surface surface;private final Runnable rendered;private MediaCodec codec;private int type;private long base=-1,last;private final Map<Integer,byte[]> headers=new HashMap<>();private volatile boolean closed;private RuntimeException outputFailure;private HandlerThread outputThread;private Handler outputHandler;private final CameraFrameClock clock=new CameraFrameClock();
    CameraDecoder(Surface surface,Runnable rendered){this.surface=surface;this.rendered=rendered;}
    synchronized void accept(int format,long timestamp,byte[] frame)throws Exception{
        if(outputFailure!=null)throw outputFailure;if(closed||!surface.isValid())return;if(type!=0&&type!=format)throw new IllegalStateException("视频编码发生变化，请重新连接");type=format;
        if(codec==null){for(byte[] nal:nalUnits(frame)){int offset=nal[2]==1?3:4;if(offset>=nal.length)continue;int kind=format==4?nal[offset]&31:(nal[offset]>>1)&63;if(format==4&&(kind==7||kind==8)||format==5&&(kind==32||kind==33||kind==34))headers.put(kind,nal);}
            if(format==4&&(!headers.containsKey(7)||!headers.containsKey(8))||format==5&&(!headers.containsKey(32)||!headers.containsKey(33)||!headers.containsKey(34)))return;
            String mime=format==4?"video/avc":"video/hevc";MediaFormat config=MediaFormat.createVideoFormat(mime,1920,1080);config.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,4*1024*1024);config.setInteger(MediaFormat.KEY_MAX_WIDTH,4096);config.setInteger(MediaFormat.KEY_MAX_HEIGHT,2160);
            if(format==4){config.setByteBuffer("csd-0",ByteBuffer.wrap(headers.get(7)));config.setByteBuffer("csd-1",ByteBuffer.wrap(headers.get(8)));}else{java.io.ByteArrayOutputStream csd=new java.io.ByteArrayOutputStream();for(int key:new int[]{32,33,34})csd.write(headers.get(key));config.setByteBuffer("csd-0",ByteBuffer.wrap(csd.toByteArray()));}
            codec=MediaCodec.createDecoderByType(mime);if(codec.getCodecInfo().getCapabilitiesForType(mime).isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency))config.setInteger(MediaFormat.KEY_LOW_LATENCY,1);codec.configure(config,surface,null,0);outputThread=new HandlerThread("JiaJuCameraOutput");outputThread.start();outputHandler=new Handler(outputThread.getLooper());codec.setOnFrameRenderedListener((c,time,nano)->{if(!closed)rendered.run();},outputHandler);codec.start();outputHandler.post(new Runnable(){public void run(){synchronized(CameraDecoder.this){if(closed)return;try{drain();}catch(RuntimeException failure){outputFailure=failure;return;}}outputHandler.postDelayed(this,8);}});
        }
        drain();int index=codec.dequeueInputBuffer(12000);if(index>=0){ByteBuffer buffer=codec.getInputBuffer(index);if(buffer==null||frame.length>buffer.capacity())throw new IllegalStateException("视频帧超过解码器容量");buffer.clear();buffer.put(frame);if(base<0)base=timestamp;long pts=Math.max(last+1,Math.max(0,timestamp-base)*1000);last=pts;codec.queueInputBuffer(index,0,frame.length,pts,0);}drain();
    }
    private void drain(){MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();for(int i=0;i<24;i++){int index=codec.dequeueOutputBuffer(info,0);if(index>=0)codec.releaseOutputBuffer(index,clock.schedule(info.presentationTimeUs,System.nanoTime()));else if(index!=MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)break;}}
    synchronized MediaFormat recordingFormat(){if(codec==null||closed)return null;try{MediaFormat output=codec.getOutputFormat();MediaFormat format=MediaFormat.createVideoFormat(type==4?"video/avc":"video/hevc",output.getInteger(MediaFormat.KEY_WIDTH),output.getInteger(MediaFormat.KEY_HEIGHT));if(type==4){if(!headers.containsKey(7)||!headers.containsKey(8))return null;format.setByteBuffer("csd-0",ByteBuffer.wrap(headers.get(7).clone()));format.setByteBuffer("csd-1",ByteBuffer.wrap(headers.get(8).clone()));}else{java.io.ByteArrayOutputStream csd=new java.io.ByteArrayOutputStream();for(int key:new int[]{32,33,34}){if(!headers.containsKey(key))return null;csd.write(headers.get(key));}format.setByteBuffer("csd-0",ByteBuffer.wrap(csd.toByteArray()));}return format;}catch(Exception e){return null;}}
    static List<byte[]> nalUnits(byte[] frame){List<byte[]> result=new ArrayList<>();int start=-1;for(int i=0;i+3<frame.length;i++){int size=frame[i]==0&&frame[i+1]==0?(frame[i+2]==1?3:frame[i+2]==0&&frame[i+3]==1?4:0):0;if(size==0)continue;if(start>=0)result.add(Arrays.copyOfRange(frame,start,i));start=i;i+=size-1;}if(start>=0)result.add(Arrays.copyOfRange(frame,start,frame.length));return result;}
    @Override public synchronized void close(){closed=true;if(outputHandler!=null)outputHandler.removeCallbacksAndMessages(null);if(outputThread!=null)outputThread.quitSafely();if(codec!=null){try{codec.stop();}catch(RuntimeException ignored){}codec.release();codec=null;}headers.clear();}
}
