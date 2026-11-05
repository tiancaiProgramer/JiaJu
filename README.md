# 家聚 · JiaJu

独立的米家与海尔设备控制 Android 应用，当前版本 **0.0.5**。设备读取和已适配控制留在家聚内，不依赖官方 App、MiLink、LSPosed 或 Home Assistant 部署。

家聚由原「家控」独立开发版本更名而来，保留包名 `dev.homecontrol.app` 和发布签名，可覆盖升级并保留账号及展示设置。本项目与融合设备卡片 / HyperFusionWidget 分开维护。

## 当前功能

- 首页设备按每行两列展示，逐项选择常驻，数量不限；「更多设备」包含全部设备。
- 首页、全部列表和隐藏设备独立排序；隐藏栏目可折叠，也可常驻。
- 账号管理、关于、桌面小组件和负一屏添加指引集中在设置中。
- 灯具电源、亮度/色温滑块、颜色与模式；已适配床头灯提供延时关闭和周期定时入口。
- 已适配电饭煲、微波炉提供模式、参数设置、食谱推荐/搜索、食材步骤及收藏页面。
- 海尔已适配油烟机提供电源、照明和风速控制面板。
- 设备控制面板刷新时保留控件，离线或读取失败保留卡片和上次状态，不自动重发控制命令。

## 使用与范围

首次在设置中自行登录米家或海尔账号。米家在小米官方网页完成登录和验证；海尔支持密码及网页授权入口。账号地区须与设备所在地一致。服务会话加密保存在手机 Android Keystore 对应的文件中，不保存账号密码，不备份到源码或 NAS 续接包。

本项目仍在开发，功能按具体型号适配，不能视为完整复刻米家：

| 范围 | 当前状态 |
| --- | --- |
| `yeelink.light.bslamp2` | 灯具面板、延时与周期定时入口；此前完成真实开关测试 |
| `chunmi.cooker.normal2` | 基础/扩展模式、自选菜单和参数预览 |
| `chunmi.cooker.js5` | 基础模式按该型号枚举适配，扩展程序尚未验证 |
| `xiaomi.microwave.cm20l` | 六类主入口、分组菜单、时间/火力或份量与预计时长 |
| 海尔 IQ75Z1 油烟机 | 独立会话、目录、状态与控制；部分控制由早期用户实测 |
| 门锁 | 显示通用可读状态，高级开锁/密码/用户管理/视频未接通 |
| 非 Hook 负一屏 | 目前提供添加指引，实际放置仍未通过验证 |

停止米家进程后，独立设备/食谱读取已验证。255 项本地检查通过；本次更名发布没有下发实体指令。烹饪、真实定时执行、收藏写入、跨地区及长期会话仍需进一步实测。详情见 [验证记录](docs/VALIDATION-0.0.5.json)。

## 构建与继续开发

环境：Python 3.9+、Git、JDK 17、Android SDK platform 35 / build-tools 36.0.0。构建不使用 Gradle；测试 JSON 运行库已附在 `tests/lib`，校验后仅用于本地测试，不打进 APK。

```sh
python3 tools/Build-JiaJu.py --check
python3 tools/Build-JiaJu.py
```

先按 [CONTINUATION.md](CONTINUATION.md) 配置工具链与自己的签名，或恢复维护者的私有 NAS 续接包。产物为 `dist/JiaJu-0.0.5.apk`。公开仓库和 Release 不包含签名私钥、密码、账号会话或手机数据。

[架构与接入](docs/ARCHITECTURE.md) · [开发续接](docs/DEVELOPMENT.md) · [更新记录](docs/CHANGELOG.md) · [最新发布](https://github.com/tiancaiProgramer/JiaJu/releases/latest)

协议参考与第三方许可证位于 `resources/META-INF/NOTICE-haier.txt`、`NOTICE-mijia.txt` 和 `tests/lib`。没有打包官方插件运行时、界面源码或视频 SDK。
