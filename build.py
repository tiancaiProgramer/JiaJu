#!/usr/bin/env python3
"""Standalone Android build: no Xposed jar or MiLink/system app dependency."""
import hashlib,json,os,pathlib,re,shutil,subprocess,zipfile
import xml.etree.ElementTree as ET
from tools.build_support import executable,sdk_command
P=pathlib.Path(__file__).resolve().parent
def option(name,legacy=None,default=None):
    value=os.environ.get(name) or (os.environ.get(legacy) if legacy else None) or default
    if value is None:raise SystemExit('Missing '+name+'; use tools/Build-JiaJu.py or configure the build environment.')
    return value
SDK=pathlib.Path(option('JIAJU_ANDROID_SDK','FUSION_ANDROID_SDK'));JDK=pathlib.Path(option('JIAJU_JAVA_HOME','FUSION_JAVA_HOME'));KEY=pathlib.Path(option('JIAJU_KEYSTORE','FUSION_KEYSTORE'));OUT=pathlib.Path(option('JIAJU_OUTPUT','FUSION_OUTPUT',str(P/'dist')))
ALIAS=option('JIAJU_KEY_ALIAS',default='fusion')
TOOLS=SDK/'build-tools/36.0.0';ANDROID=SDK/'platforms/android-35/android.jar';BUILD=P/'build'
VERSION=ET.parse(P/'AndroidManifest.xml').getroot().get('{http://schemas.android.com/apk/res/android}versionName')
env=os.environ.copy();env['JAVA_HOME']=str(JDK);env['PATH']=str(JDK/'bin')+os.pathsep+env.get('PATH','')
env['JIAJU_SIGN_STORE_PASSWORD']=option('JIAJU_KEYSTORE_PASSWORD','FUSION_KEYSTORE_PASSWORD')
env['JIAJU_SIGN_KEY_PASSWORD']=option('JIAJU_KEY_PASSWORD',default=env['JIAJU_SIGN_STORE_PASSWORD'])
def run(*args):subprocess.run([str(a) for a in args],cwd=P,env=env,check=True)
if BUILD.exists():shutil.rmtree(BUILD)
for d in [BUILD/'generated/dev/homecontrol/app',BUILD/'classes',BUILD/'tests',BUILD/'dex',OUT]:d.mkdir(parents=True,exist_ok=True)
run(os.sys.executable,P/'native-camera/build.py')
run(executable(JDK/'bin','keytool'),'-exportcert','-keystore',KEY,'-storepass:env','JIAJU_SIGN_STORE_PASSWORD','-alias',ALIAS,'-file',BUILD/'certificate.der')
digest=hashlib.sha256((BUILD/'certificate.der').read_bytes()).hexdigest()
(BUILD/'generated/dev/homecontrol/app/BuildIdentity.java').write_text('package dev.homecontrol.app; final class BuildIdentity {static final String CERT_SHA256="'+digest+'",VERSION_NAME='+json.dumps(VERSION)+';}')
run(*sdk_command(TOOLS,'aapt2',JDK),'compile','--dir',P/'res','-o',BUILD/'resources.zip')
run(*sdk_command(TOOLS,'aapt2',JDK),'link','-I',ANDROID,'--manifest',P/'AndroidManifest.xml','--java',BUILD/'generated','--auto-add-overlay','-o',BUILD/'unsigned.apk',BUILD/'resources.zip')
sources=sorted((P/'src').rglob('*.java'))+sorted((BUILD/'generated').rglob('*.java'))
run(executable(JDK/'bin','javac'),'-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-classpath',ANDROID,'-d',BUILD/'classes',*sources)
JSON_TEST=P/'tests/lib/android-json.jar'
if hashlib.sha256(JSON_TEST.read_bytes()).hexdigest()!='dfb7bae2f404cfe0b72b4d23944698cb716b7665171812a0a4d0f5926c0fac79':raise RuntimeError('Test JSON runtime checksum mismatch')
run(executable(JDK/'bin','javac'),'-encoding','UTF-8','-classpath',str(BUILD/'classes')+os.pathsep+str(JSON_TEST),'-d',BUILD/'tests',*sorted((P/'tests').glob('*.java')))
for test in ['HaierTest','HaierWireTest','HaierPanelQueueTest','PresentationTest','MijiaWireTest','MijiaModelTest','MijiaCommandTest','FeaturesTest','CameraPolicyTest','CameraPlaybackTest','CameraReuseTest','PanelLayoutTest']:run(executable(JDK/'bin','java'),'-cp',str(BUILD/'classes')+os.pathsep+str(BUILD/'tests')+os.pathsep+str(JSON_TEST),'dev.homecontrol.app.'+test)
run(executable(JDK/'bin','jar'),'cf',BUILD/'app.jar','-C',BUILD/'classes','.')
run(*sdk_command(TOOLS,'d8',JDK),'--min-api','31','--lib',ANDROID,'--output',BUILD/'dex',BUILD/'app.jar')
with zipfile.ZipFile(BUILD/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
    for d in (BUILD/'dex').glob('*.dex'):z.write(d,d.name)
    for library in (BUILD/'native').rglob('*.so'):z.write(library,'lib/'+library.relative_to(BUILD/'native').as_posix())
    for r in (P/'resources').rglob('*'):
        if r.is_file():z.write(r,r.relative_to(P/'resources').as_posix())
run(*sdk_command(TOOLS,'zipalign',JDK),'-f','4',BUILD/'unsigned.apk',BUILD/'aligned.apk')
result=OUT/f'JiaJu-{VERSION}.apk'
run(*sdk_command(TOOLS,'apksigner',JDK),'sign','--ks',KEY,'--ks-key-alias',ALIAS,'--ks-pass','env:JIAJU_SIGN_STORE_PASSWORD','--key-pass','env:JIAJU_SIGN_KEY_PASSWORD','--v4-signing-enabled','false','--out',result,BUILD/'aligned.apk')
run(*sdk_command(TOOLS,'apksigner',JDK),'verify','--verbose','--print-certs',result)
result.with_suffix('.apk.sha256').write_text(hashlib.sha256(result.read_bytes()).hexdigest()+'  '+result.name+'\n');print('Built:',result)
