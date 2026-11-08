"""Host tool paths shared by the portable Android build scripts."""
import os
import pathlib


def executable(directory, name):
    return pathlib.Path(directory) / (name + ('.exe' if os.name == 'nt' else ''))


def sdk_command(tools, name, jdk):
    tools = pathlib.Path(tools)
    if os.name == 'nt' and name in ('d8', 'apksigner'):
        java = executable(pathlib.Path(jdk) / 'bin', 'java')
        if name == 'd8':
            return [java, '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.D8']
        return [java, '-jar', tools / 'lib/apksigner.jar']
    return [executable(tools, name)]


def ndk_compiler(ndk):
    prebuilt = pathlib.Path(ndk) / 'toolchains/llvm/prebuilt'
    if os.name == 'nt':
        clang = prebuilt / 'windows-x86_64/bin/clang.exe'
        if clang.is_file():
            # Go splits CC itself. Invoke clang directly instead of an NDK .cmd launcher.
            return '"' + clang.as_posix() + '" --target=aarch64-linux-android31'
    else:
        for host in ('darwin-x86_64', 'linux-x86_64'):
            clang = prebuilt / host / 'bin/aarch64-linux-android31-clang'
            if clang.is_file():
                return '"' + clang.as_posix() + '"'
    raise SystemExit('Missing NDK r26+ compiler; update JIAJU_ANDROID_NDK.')
