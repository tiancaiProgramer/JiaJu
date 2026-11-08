# 继续开发 · v0.0.12

当前源码、Windows 构建适配和设备面板改动一起发布在 `v0.0.12`，包名仍为 `dev.homecontrol.app`。新机器先阅读根目录 `AGENTS.md`、`README.md`、`CONTINUATION.md` 和本文件。

## 获取源码和安装包

```sh
git clone https://github.com/tiancaiProgramer/JiaJu.git
cd JiaJu
git checkout v0.0.12
```

需要继续提交时，从该标签创建自己的开发分支，或切回已包含此版本的 `main`。对应安装包在 [v0.0.12 Release](https://github.com/tiancaiProgramer/JiaJu/releases/tag/v0.0.12)。APK SHA256：

```text
7c8fb565c0009f8300c2d0ddb436f0b771535a9d10682f026595b93bc8085988
```

## 本机构建

使用 Python 3.9+、JDK 17、Android platform 35 / build-tools 36.0.0、Go 1.26+ 与 Android NDK r26+；当前 Windows 验证使用 Go 1.26.10 和 NDK 28.2.13676358，不需要 WSL。原生摄像机组件目前只构建 arm64-v8a。

1. 将 `config/build-environment.example.json` 复制到 `local/build-environment.json`，填写本机 JDK、SDK、Go、NDK 路径。Windows 路径可写为 `G:/tools/...`。
2. 从维护者自己的私有备份恢复 `private-signing/` 和本机签名配置。公开 GitHub 不含签名私钥或口令；自行生成的新签名无法覆盖已有维护者签名的安装。不要卸载现有应用来绕过签名不匹配，以免清除登录与本地设置。
3. 执行 `python tools/Build-JiaJu.py --check`，再执行 `python tools/Build-JiaJu.py`。macOS/Linux 使用相应 Python 命令和本机工具链。

受限 Windows 临时目录不可写时，可以把 `TEMP`、`TMP` 指向项目内已创建的 `local/build-temp`。输出目录用 `JIAJU_OUTPUT` 指定一个新的 `dist/` 子目录，保留旧 APK 和验收日志。

## 当前实现和验证位置

- `PanelLayout`、`MijiaPanelActivity`：插座/空调的单行用电信息、常用按钮与更多分组；`AppSheet`、`AppTimeInput`：统一编辑外观和时间输入。
- `CameraActivity`：竖屏下方操作、全屏、存储和更多设置；`CameraCaptures` / `CameraRecording`：显式保存截图及视频；当前录像没有音轨。
- `CameraAudio`、`NativeCameraSession`、`native-camera/`：独立视频、声音、对讲、云台和存储卡回看。没有官方 SDK 运行时依赖或公开 HTTP 控制端口。
- `CameraStorage` / `CameraNas`：按精确型号映射存储属性及 NAS RPC；访问凭据只放在当前表单内存。
- [验证汇总](VALIDATION-0.0.12.json)记录实际 APK 哈希、不同轮次的来源和边界；`testing/` 中仅版本化已审阅的脱敏 JSON/JSONL。原始日志、编译输出和官方程序研究副本不在公开仓库中。

本地检查：588 项 JVM、7 项 Python、16 个 Go 测试函数通过。四台在线监控通过每台 18 项页面检查；最后的循环任务文字调整后，最终安装包又通过插座和原 069a01 摄像机回归。不要把中间 APK 的测试时长改记成最终 APK 的测试。

同签名的临时检查包通过 `python tools/Build-JiaJu.py --device-check` 构建。`tools/Check-JiaJuCamera.py --help` 提供真实视频页检查入口，需先获得用户观看授权。检查后移除 `dev.homecontrol.tests`，保留主应用和用户数据。

## 后续优先事项

1. 实测按住麦克风的完整通话路径。用户本轮已听到 1 号监控的合成短音，但该测试没有采集麦克风。
2. 确认云台真实移动方向和幅度。本轮发送过一次移动指令，用户没有注意到，不能记为物理验收通过。
3. 使用用户指定的 NAS 验证搜索、共享认证、保存与长期备份。当前三种型号只读配置均通过，没有改写实际 NAS 设置。
4. 继续验证其它摄像机的 SD 回看、较长时间播放、高清和弱网。目录传输可能需要数十秒，起播也仍可能遇到中继未就绪。
5. 等离线设备通电后再处理 `039a01`、`029a02` 和离线的 `069a01`。不要扩大精确型号白名单来假装适配。

视频复用与播放后自动恢复开关仍为 `false`。实体控制不得自动重发；云端回执不能代替物理效果。真实设置、移动、播放测试音等按 `AGENTS.md` 获取当前测试授权。账号、地区、型号、在线状态和解锁校验必须保留。
