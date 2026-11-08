"""Check host command selection without invoking SDK tools or accessing credentials."""
import pathlib
import sys
import tempfile
import types
import unittest
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / 'tools'))
import build_support


class BuildSupportTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='jiaju build ')
        self.addCleanup(self.temporary.cleanup)
        self.root = pathlib.Path(self.temporary.name)

    def host(self, name):
        return mock.patch.object(build_support, 'os', types.SimpleNamespace(name=name))

    def touch(self, relative):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.touch()
        return path

    def test_windows_executables(self):
        with self.host('nt'):
            self.assertEqual(build_support.executable(self.root, 'go'), self.root / 'go.exe')
            self.assertEqual(build_support.sdk_command(self.root, 'aapt2', self.root), [self.root / 'aapt2.exe'])

    def test_windows_java_sdk_commands_avoid_batch_launchers(self):
        with self.host('nt'):
            java = self.root / 'bin/java.exe'
            self.assertEqual(build_support.sdk_command(self.root, 'd8', self.root),
                             [java, '-cp', self.root / 'lib/d8.jar', 'com.android.tools.r8.D8'])
            self.assertEqual(build_support.sdk_command(self.root, 'apksigner', self.root),
                             [java, '-jar', self.root / 'lib/apksigner.jar'])

    def test_unix_commands_keep_existing_launchers(self):
        with self.host('posix'):
            self.assertEqual(build_support.executable(self.root, 'go'), self.root / 'go')
            for name in ('d8', 'apksigner', 'aapt2', 'zipalign'):
                self.assertEqual(build_support.sdk_command(self.root, name, self.root), [self.root / name])

    def test_windows_ndk_has_explicit_android_target_and_quoted_path(self):
        clang = self.touch('toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe')
        with self.host('nt'):
            self.assertEqual(build_support.ndk_compiler(self.root),
                             '"' + clang.as_posix() + '" --target=aarch64-linux-android31')

    def test_linux_ndk_launcher(self):
        clang = self.touch('toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android31-clang')
        with self.host('posix'):
            self.assertEqual(build_support.ndk_compiler(self.root), '"' + clang.as_posix() + '"')

    def test_macos_ndk_launcher(self):
        clang = self.touch('toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android31-clang')
        with self.host('posix'):
            self.assertEqual(build_support.ndk_compiler(self.root), '"' + clang.as_posix() + '"')

    def test_missing_ndk_fails_before_build(self):
        for host in ('nt', 'posix'):
            with self.host(host), self.assertRaisesRegex(SystemExit, 'JIAJU_ANDROID_NDK'):
                build_support.ndk_compiler(self.root)


if __name__ == '__main__':
    unittest.main()
