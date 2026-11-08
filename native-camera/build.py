#!/usr/bin/env python3
"""Build the in-app arm64 camera process; no credentials or media enter the build."""
import os,pathlib,shutil,subprocess,sys
ROOT=pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT/'tools'))
from build_support import executable,ndk_compiler
go_home=os.environ.get('JIAJU_GO_HOME','');go=executable(pathlib.Path(go_home)/'bin','go') if go_home else shutil.which('go')
if not go or not pathlib.Path(go).is_file():raise SystemExit('Set JIAJU_GO_HOME to Go 1.26+ for the embedded camera build.')
ndk=pathlib.Path(os.environ.get('JIAJU_ANDROID_NDK',''))
compiler=ndk_compiler(ndk)
out=ROOT/'build/native/arm64-v8a/libjiaju_camera.so';out.parent.mkdir(parents=True,exist_ok=True)
host_env=os.environ.copy();host_env.update({'GOPATH':str(ROOT/'.local/go'),'GOCACHE':str(ROOT/'.local/go-cache'),'CGO_ENABLED':'0'});host_env.pop('GOOS',None);host_env.pop('GOARCH',None)
subprocess.run([str(go),'test','-mod=vendor','./...'],cwd=ROOT/'native-camera',env=host_env,check=True)
env=os.environ.copy();env.update({'GOOS':'android','GOARCH':'arm64','CGO_ENABLED':'1','CC':str(compiler),'GOPATH':str(ROOT/'.local/go'),'GOCACHE':str(ROOT/'.local/go-cache')})
subprocess.run([str(go),'build','-mod=vendor','-trimpath','-buildvcs=false','-ldflags=-s -w -extldflags=-Wl,-z,max-page-size=16384','-o',str(out),'.'],cwd=ROOT/'native-camera',env=env,check=True)
print('Built embedded camera process:',out)
