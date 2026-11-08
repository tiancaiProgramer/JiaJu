#!/usr/bin/env python3
"""Build a separate same-signer checker: read-only cloud checks plus reversible local layout checks."""
import os,pathlib,subprocess,sys,zipfile
P=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(P/'tools'))
from build_support import executable,sdk_command
SDK=pathlib.Path(os.environ['JIAJU_ANDROID_SDK']);JDK=pathlib.Path(os.environ['JIAJU_JAVA_HOME']);KEY=pathlib.Path(os.environ['JIAJU_KEYSTORE']);T=SDK/'build-tools/36.0.0';ANDROID=SDK/'platforms/android-35/android.jar';B=P/'build/device-check'
for d in [B,B/'classes',B/'dex']:d.mkdir(parents=True,exist_ok=True)
env=os.environ.copy();env['JAVA_HOME']=str(JDK);env['PATH']=str(JDK/'bin')+os.pathsep+env.get('PATH','');env['JIAJU_SIGN_STORE_PASSWORD']=os.environ['JIAJU_KEYSTORE_PASSWORD']
env['JIAJU_SIGN_KEY_PASSWORD']=env.get('JIAJU_KEY_PASSWORD',env['JIAJU_KEYSTORE_PASSWORD'])
def run(*args):subprocess.run([str(a) for a in args],env=env,check=True)
(B/'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.homecontrol.tests"><uses-sdk android:minSdkVersion="31" android:targetSdkVersion="35"/><application android:label="家聚读取检查" android:allowBackup="false"/><instrumentation android:name="dev.homecontrol.app.MijiaDeviceCheckInstrumentation" android:targetPackage="dev.homecontrol.app" android:functionalTest="true"/><instrumentation android:name="dev.homecontrol.app.LayoutDeviceCheck" android:targetPackage="dev.homecontrol.app" android:functionalTest="true"/></manifest>''',encoding='utf-8')
run(*sdk_command(T,'aapt2',JDK),'link','-I',ANDROID,'--manifest',B/'AndroidManifest.xml','-o',B/'unsigned.apk')
run(executable(JDK/'bin','javac'),'-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-classpath',str(ANDROID)+os.pathsep+str(P/'build/classes'),'-d',B/'classes',P/'tests/android/MijiaDeviceCheckInstrumentation.java',P/'tests/android/LayoutDeviceCheck.java',P/'tests/android/SyntheticMediaCheck.java')
run(executable(JDK/'bin','jar'),'cf',B/'check.jar','-C',B/'classes','.')
run(*sdk_command(T,'d8',JDK),'--min-api','31','--lib',ANDROID,'--classpath',P/'build/app.jar','--output',B/'dex',B/'check.jar')
with zipfile.ZipFile(B/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:z.write(B/'dex/classes.dex','classes.dex')
run(*sdk_command(T,'zipalign',JDK),'-f','4',B/'unsigned.apk',B/'aligned.apk')
run(*sdk_command(T,'apksigner',JDK),'sign','--ks',KEY,'--ks-key-alias',env.get('JIAJU_KEY_ALIAS','fusion'),'--ks-pass','env:JIAJU_SIGN_STORE_PASSWORD','--key-pass','env:JIAJU_SIGN_KEY_PASSWORD','--v4-signing-enabled','false','--out',B/'JiaJuReadCheck.apk',B/'aligned.apk')
print('Separate device/UI check:',B/'JiaJuReadCheck.apk')
