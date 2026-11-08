# 2026-11-08 设备面板与摄像机协议核对

- 面板测试夹具只保存型号功能定义，删除所有值、账号、设备 ID、家庭与房间信息。212a01 的能耗缩放依据 Xiaomi 官方 ha_xiaomi_home 的 spec_modify.yaml：raw × 6 / 1,000,000 kWh；电功率由定义的 0.01 W 换算。cuco.plug.v3 的能耗为 0.01 kWh。未确认的电流/电压缩放没有猜测。
- cuco 循环任务的六段格式保留重复日期及末尾模式；未识别的非空格式不转换为默认值。
- MISS 命令编号、媒体编码值、CS2 channel 3 音频封包参考 AlexxIT/go2rtc 及 xiaomi-camera-viewer 的公开协议实现。二进制 speaker response 由授权官方程序核对为本机 int32；仅零码确认，未知码不当作成功。
- RDT 目录请求为 little-endian command 6、空 payload；当前三型号以官方程序的 TLV 定义为准，未照搬其它摄像机所需的 24 字节 channel 请求。响应分段拼接，只有精确片段起点才能回看。目录读取有大小与时间上限，未实现文件下载。
- NAS 方法和 share 字段通过授权官方程序核对：nas_get_config / nas_scan / nas_list_dir / nas_set_config。仅允许当前账号/地区的三个精确型号，未使用官方运行时、跳转官方页面或保存访问密码。
- 官方程序的研究副本仅放在本机排除目录，不属于公开源码、构建输入、续接包或发布内容。生产代码为独立实现，没有包含官方程序字节。

参考来源：

- https://github.com/XiaoMi/ha_xiaomi_home/blob/main/custom_components/xiaomi_home/miot/specs/spec_modify.yaml
- https://github.com/AlexxIT/go2rtc/tree/master/pkg/xiaomi/miss
- https://github.com/lubos-hnanicek/xiaomi-camera-viewer/tree/main/bridge/internal/recordings
- https://github.com/lubos-hnanicek/xiaomi-camera-viewer/tree/main/bridge/internal/stream

下载的公开 Go 参考文件及其 MIT 许可证仅保存在本机 20261108-protocol/，不纳入本仓库源码；以上链接供协议事实溯源。应用现有第三方声明继续保留。
