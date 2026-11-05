#!/usr/bin/env python3
"""Build a separate, same-signer read-only instrumentation APK. Not a production component."""
import os,pathlib,subprocess,zipfile
P=pathlib.Path(__file__).resolve().parents[1]
SDK=pathlib.Path(os.environ['JIAJU_ANDROID_SDK']);JDK=pathlib.Path(os.environ['JIAJU_JAVA_HOME']);KEY=pathlib.Path(os.environ['JIAJU_KEYSTORE']);T=SDK/'build-tools/36.0.0';ANDROID=SDK/'platforms/android-35/android.jar';B=P/'build/device-check'
for d in [B,B/'classes',B/'dex']:d.mkdir(parents=True,exist_ok=True)
env=os.environ.copy();env['JAVA_HOME']=str(JDK);env['PATH']=str(JDK/'bin')+os.pathsep+env.get('PATH','');env['JIAJU_SIGN_STORE_PASSWORD']=os.environ['JIAJU_KEYSTORE_PASSWORD']
env['JIAJU_SIGN_KEY_PASSWORD']=env.get('JIAJU_KEY_PASSWORD',env['JIAJU_KEYSTORE_PASSWORD'])
def run(*args):subprocess.run([str(a) for a in args],env=env,check=True)
(B/'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.homecontrol.tests"><uses-sdk android:minSdkVersion="31" android:targetSdkVersion="35"/><application android:label="家聚读取检查" android:allowBackup="false"/><instrumentation android:name="dev.homecontrol.app.MijiaDeviceCheckInstrumentation" android:targetPackage="dev.homecontrol.app" android:functionalTest="true"/></manifest>''')
run(T/'aapt2','link','-I',ANDROID,'--manifest',B/'AndroidManifest.xml','-o',B/'unsigned.apk')
run(JDK/'bin/javac','-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-classpath',str(ANDROID)+os.pathsep+str(P/'build/classes'),'-d',B/'classes',P/'tests/android/MijiaDeviceCheckInstrumentation.java')
run(JDK/'bin/jar','cf',B/'check.jar','-C',B/'classes','.')
run(T/'d8','--min-api','31','--lib',ANDROID,'--classpath',P/'build/app.jar','--output',B/'dex',B/'check.jar')
with zipfile.ZipFile(B/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:z.write(B/'dex/classes.dex','classes.dex')
run(T/'zipalign','-f','4',B/'unsigned.apk',B/'aligned.apk')
run(T/'apksigner','sign','--ks',KEY,'--ks-key-alias',env.get('JIAJU_KEY_ALIAS','fusion'),'--ks-pass','env:JIAJU_SIGN_STORE_PASSWORD','--key-pass','env:JIAJU_SIGN_KEY_PASSWORD','--v4-signing-enabled','false','--out',B/'JiaJuReadCheck.apk',B/'aligned.apk')
print('Read-only device check:',B/'JiaJuReadCheck.apk')
