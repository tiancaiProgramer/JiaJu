package dev.homecontrol.app;

import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import org.json.JSONObject;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.security.KeyStore;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;

/** Separate Keystore key and no-backup file for the user's Mi Home cloud session. */
final class MijiaVault implements AutoCloseable {
    private final FileChannel channel;private final FileLock lock;private final AtomicFile file;private final SecretKey key;
    MijiaVault(Context c)throws Exception {
        channel=new RandomAccessFile(new File(c.getNoBackupFilesDir(),"mijia.lock"),"rw").getChannel();FileLock acquired=null;try{acquired=channel.tryLock();}catch(OverlappingFileLockException ignored){}if(acquired==null){channel.close();throw new IOException("米家正在同步，请稍后重试");}lock=acquired;
        try{file=new AtomicFile(new File(c.getNoBackupFilesDir(),"mijia-session.enc"));KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);String alias="homecontrol.mijia.session.v1";if(!store.containsAlias(alias)){KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();}key=(SecretKey)store.getKey(alias,null);}catch(Exception e){lock.release();channel.close();throw e;}
    }
    JSONObject read()throws Exception{byte[] data;try{data=file.readFully();}catch(FileNotFoundException absent){return new JSONObject();}if(data.length<29||data[0]!=1)throw new IOException("米家登录数据无效，请重新登录");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,data,1,12));return new JSONObject(new String(c.doFinal(data,13,data.length-13),StandardCharsets.UTF_8));}
    void write(JSONObject value)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);byte[] encrypted=c.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));FileOutputStream out=null;try{out=file.startWrite();out.write(1);out.write(c.getIV());out.write(encrypted);file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}}
    void clear(){file.delete();}
    @Override public void close()throws IOException{lock.release();channel.close();}
}
