# 架构与接入

家聚为原生 Android Java 应用，无 Xposed、MiLink 或 Home Assistant 部署依赖。最低 Android 12，当前在 Android 16 / HyperOS 环境开发。

## 展示

`MainActivity`、`DevicePresentation`、`DisplayPreferences` 管理常驻、全部、隐藏及各自排序。`DeviceStore` 合并两个平台的目录，缺失的常驻设备仍保留。账号和小组件入口在 `SettingsActivity`，标准 Android 桌面组件由 `DeviceWidget` 提供。非 Hook 负一屏当前只保留指引。

## 米家

- `MijiaLogin` / `MijiaLoginActivity`：官方网页挑战、用户自行验证及 STS 会话，不读取官方 App 的账号数据库。
- `MijiaVault`：Android Keystore AES-GCM 会话与原生设备映射，禁止备份；不持久化账号密码。
- `MijiaCloud` / `MiHomeApi`：目录、RC4-drop1024 请求、模型、状态与受限功能分发。设备摘要 ID 绑定账号、地区和原生 DID，原生 DID/MAC 不进入 Activity 参数。
- `MijiaSpec` / `MijiaModel` / `MijiaCommand`：公开 MIoT 模型、范围/枚举/输入验证、状态保留和完整回执匹配。
- `MijiaPanelActivity`：常驻底部面板；刷新更新值，滑块仅在用户完成拖动后发出写操作。
- `MijiaFeatures` / `JoyamiClient`：按精确型号读取厂商菜单和食谱，不运行官方插件。
- `RecipesActivity` / `FeatureData`：原生模式、推荐、搜索、收藏及文本详情，清理 HTML。
- `KitchenProgram` / `KitchenState`：设备返回的程序、CRC、参数/份量时间表和新鲜状态校验；启动前再次读取状态，不接受外部任意脚本。
- `LightSchedules` / `LightTimerActivity`：床头灯设备延时及云端周期定时；只修改绑定本设备属性的自建周期任务，并兼容旧品牌任务名。

## 海尔

`HaierActivity`、`HaierOAuthActivity`、`HaierVault`、`HaierClient`、`HaierSocket`、`HaierCloud` 和 `HaierPanelActivity` 负责独立认证、目录、模型、网关及油烟机控制。会话与米家隔离；`CloudDevices` 隔离各平台刷新失败。

海尔密码入口采用社区协议；网页短信试验使用公开 Home Assistant 授权配置，页面可能显示该名称，不代表项目拥有海尔官方应用资质，也不要求用户部署 Home Assistant。短信授权完成、长期续期与更多型号仍需验证。

## 命令与验证

`HaierPanelQueue` 串行执行待处理操作，属性写可合并，失败不自动重发。确认云端受理与确认实体状态分别报告。读失败保留上次值，未知状态不填默认关闭或零。

模型存在、独立接口读取、参数预览、云端回执与实体操作是不同层次。更名前验证过床头灯短暂开关并恢复；厨房程序、食谱和定时本轮主要完成读取与页面验证。门锁的一次性密码、安全蓝牙、用户管理和 P2P 视频尚未实现，不能由通用云端会话直接替代。

协议参考：

- https://github.com/PiotrMachowski/Xiaomi-cloud-tokens-extractor（MIT）
- https://github.com/al-one/hass-xiaomi-miot（Apache-2.0）
- https://github.com/banto6/haier
- https://github.com/MiEcosystem/miot-plugin-sdk
- https://github.com/MiEcosystem/NewXmPluginSDK

实现独立编写，引用许可见 `resources/META-INF/`；没有移植限制独立 App 使用的官方 HA 集成源码、OAuth 凭据或官方插件运行时。
