# 构建与 NAS 恢复

## 公开源码构建

1. 安装 Python 3.9+、Git、JDK 17、Android SDK platform 35 / build-tools 36.0.0、Go 1.26+ 及 Android NDK r26+。
2. 将 `config/build-environment.example.json` 复制为 `local/build-environment.json`，修改 JDK/SDK、`JIAJU_GO_HOME` 和 `JIAJU_ANDROID_NDK` 的实际路径。macOS 的 JDK 路径通常以 `Contents/Home` 结尾，Linux 使用 JDK 根目录。
3. 使用自己的 Android 签名，设置 `JIAJU_KEYSTORE`、`JIAJU_KEY_ALIAS`、`JIAJU_KEYSTORE_PASSWORD`，必要时设置 `JIAJU_KEY_PASSWORD`。也可放在本机的 `private-signing/signing-credentials.json` 中，格式见下方。不要提交真实凭据。
4. 执行 `python3 tools/Build-JiaJu.py --check`，然后执行 `python3 tools/Build-JiaJu.py`。产物写入 `dist/`。

```json
{
  "KEYSTORE_PATH": "private-signing/your-signing.jks",
  "KEY_ALIAS": "your-alias",
  "KEYSTORE_PASSWORD": "填写自己的口令",
  "KEY_PASSWORD": "填写自己的密钥口令"
}
```

自己的签名与维护者签名不同，不能直接覆盖维护者发布的 APK。官方升级需要保留相同包名与发布证书。

## 维护者私有 NAS 续接包

续接包单独交付，不在 GitHub Release 中。它包含：

- 已发布源码的 Git bundle：默认可在无网络时恢复。
- 对应 APK、提交/发布/校验信息。
- 同一份 Android 签名密钥与本机口令配置。
- 本机工具链路径、开发交接说明、恢复和构建脚本。

不包含 SDK/JDK/Go/NDK、GitHub 登录凭据、米家/海尔会话、手机数据库或官方插件研究文件。

解压后在包目录执行：

```sh
python3 tools/Restore-JiaJu.py --verify-only
python3 tools/Restore-JiaJu.py /新的目录/JiaJu
```

脚本拒绝覆盖已有目录，先校验包内容，再克隆 Git bundle 并检出保存的提交，恢复私有签名与配置，设置公开 GitHub origin，同时将私有文件加入 Git 排除规则。也可加 `--from-github` 从 GitHub 克隆后恢复到保存的提交。

进入新项目，修改 `local/build-environment.json`，再运行构建检查及构建。保存的 APK 位于 `.local/saved-release/`。

macOS/Linux 为当前构建环境。Windows 原生路径与 SDK 启动器尚未验证，建议在 WSL 中使用 Linux JDK/SDK，不能直接复用 macOS 的工具链二进制文件。后续 GitHub 推送需要自行登录，不会从 NAS 包恢复 GitHub 账号。
