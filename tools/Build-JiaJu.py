#!/usr/bin/env python3
"""Build from portable local configuration; signing secrets are never command arguments."""
import argparse,hashlib,json,os,pathlib,subprocess,sys

ROOT=pathlib.Path(__file__).resolve().parents[1]

def configuration():
    env=os.environ.copy()
    file=ROOT/'local/build-environment.json'
    config=json.loads(file.read_text(encoding='utf-8')) if file.is_file() else {}
    private=ROOT/'private-signing/signing-credentials.json'
    signing=json.loads(private.read_text(encoding='utf-8')) if private.is_file() else {}
    aliases={'JIAJU_KEYSTORE':'KEYSTORE_PATH','JIAJU_KEY_ALIAS':'KEY_ALIAS','JIAJU_KEYSTORE_PASSWORD':'KEYSTORE_PASSWORD','JIAJU_KEY_PASSWORD':'KEY_PASSWORD'}
    for key in ['JIAJU_JAVA_HOME','JIAJU_ANDROID_SDK','JIAJU_OUTPUT']:
        if not env.get(key) and config.get(key):env[key]=config[key]
    for key,field in aliases.items():
        if not env.get(key) and signing.get(field):env[key]=signing[field]
    env.setdefault('JIAJU_KEY_ALIAS','fusion')
    for key in ['JIAJU_JAVA_HOME','JIAJU_ANDROID_SDK','JIAJU_KEYSTORE','JIAJU_OUTPUT']:
        if key in env:
            path=pathlib.Path(env[key]).expanduser()
            env[key]=str(path if path.is_absolute() else ROOT/path)
    required=['JIAJU_JAVA_HOME','JIAJU_ANDROID_SDK','JIAJU_KEYSTORE','JIAJU_KEYSTORE_PASSWORD']
    missing=[key for key in required if not env.get(key)]
    if missing:raise SystemExit('Missing configuration: '+', '.join(missing)+'. See CONTINUATION.md.')
    jdk=pathlib.Path(env['JIAJU_JAVA_HOME']);sdk=pathlib.Path(env['JIAJU_ANDROID_SDK']);tools=sdk/'build-tools/36.0.0'
    for path in [jdk/'bin/java',jdk/'bin/javac',jdk/'bin/jar',jdk/'bin/keytool',sdk/'platforms/android-35/android.jar',tools/'aapt2',tools/'d8',tools/'zipalign',tools/'apksigner',pathlib.Path(env['JIAJU_KEYSTORE'])]:
        if not path.is_file():raise SystemExit('Missing file; update local/build-environment.json: '+str(path))
    env['JAVA_HOME']=str(jdk);env['PATH']=str(jdk/'bin')+os.pathsep+env.get('PATH','')
    env['JIAJU_CHECK_STORE_PASSWORD']=env['JIAJU_KEYSTORE_PASSWORD']
    certificate=subprocess.check_output([str(jdk/'bin/keytool'),'-exportcert','-keystore',env['JIAJU_KEYSTORE'],'-storepass:env','JIAJU_CHECK_STORE_PASSWORD','-alias',env['JIAJU_KEY_ALIAS']],env=env,stderr=subprocess.DEVNULL)
    digest=hashlib.sha256(certificate).hexdigest()
    info=ROOT/'private-signing/signing-info.json'
    if info.is_file() and json.loads(info.read_text(encoding='utf-8'))['certificate_sha256']!=digest:raise SystemExit('Signing certificate mismatch; refusing an incompatible update.')
    return env,digest

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true',help='Check configuration, tools and signing identity without building')
    parser.add_argument('--device-check',action='store_true',help='Build the separate read-only instrumentation APK after the app')
    args=parser.parse_args();env,digest=configuration()
    print('Toolchain and signing identity verified:',digest,flush=True)
    if args.check:return
    subprocess.run([sys.executable,str(ROOT/'build.py')],cwd=ROOT,env=env,check=True)
    if args.device_check:subprocess.run([sys.executable,str(ROOT/'tests/build-device-check.py')],cwd=ROOT,env=env,check=True)

if __name__=='__main__':main()
