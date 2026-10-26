# Shizuku 临时 Root

通过 **Shizuku（无线 ADB）** 在手机上直接执行 GhostLock / FNE 内核漏洞，获取**本次开机有效**的临时 root（基于 FIFO 通道），全程不需要电脑。

> ⚠️ **仅限你拥有合法处置权的设备。** 内核漏洞利用具有概率性，失败可能导致内核 panic 重启，并可能损坏文件系统数据。使用前请备份重要数据。

## 功能

- 一键获取 Root：释放工具链 → 触发内核漏洞 → 建立常驻 root 通道
- 检查状态：查看工具链、进程、通道和 SELinux 状态
- 执行命令：通过 root 通道以 uid=0 执行任意命令（不依赖 KernelSU 的 `su`）
- 恢复 SELinux：把 SELinux 切回 Enforcing

## 前置条件

- 手机已安装 **Shizuku**，并通过「无线调试」启动服务（开发者选项 → 无线调试）
- Android 8.0 及以上
- 仅适用于 HONOR 50（NTH-AN00）/ MagicOS 8.0.0.221 / 内核 5.4.289-qgki 及其同固件变体（其他机型大概率不兼容）

## 使用

1. 启动 Shizuku（无线调试）
2. 安装 `ShizukuRoot.apk`
3. 打开 App → 点「授权 Shizuku」→ 手机上允许
4. 点「一键获取 Root」，等待 1–5 分钟（漏洞会自动重试）
5. 状态显示 **ROOT SHELL CHANNEL UP** 即成功，可用「执行命令」跑 `id` 验证

## 原理

```
Shizuku shell 权限 (uid 2000)
  → 释放工具链到 /data/local/tmp/ksu_fne/
  → sh fne_local.sh（GhostLock/FNE 内核漏洞，W1 关 SELinux + W2 提权）
  → chain_hook.sh 建立 root FIFO 通道（/data/local/tmp/rs.in / rs.out）
  → 后续 root 命令经通道执行
```

不写入系统分区，不修改 boot，root 仅在本次开机有效；重启后自动恢复 SELinux。

## 构建

需要：JDK 17、Android SDK Build-Tools 36.0.0、Platform android-35、Python 3.10+（含 Pillow）。

```powershell
# 1. 生成图标（如已有 res/mipmap-* 可跳过）
python make_icon.py
Copy-Item icon\mipmap-*\ic_launcher.png res\ -Recurse -Force

# 2. 构建 APK（按需修改 build.ps1 里的 SDK/JDK 路径，或用环境变量）
$env:ANDROID_SDK_ROOT = "C:\path\to\sdk"
$env:JAVA_HOME = "C:\path\to\jdk17"
.\build.ps1
# 输出 out\ShizukuRoot.apk
```

`build.ps1` 默认读取 `ANDROID_SDK_ROOT` 和 `JAVA_HOME`。

## 目录结构

```
AndroidManifest.xml
build.ps1                 构建脚本（javac → d8 → aapt → zipalign → apksigner）
make_icon.py              生成多分辨率图标
src/com/smr/rootapp/      App 源码
res/mipmap-*              图标
assets/payload.bin        工具链（gzip tar）
payload/ksu_fne/          工具链原始文件（构建输入）
libs/                     Shizuku API / AIDL / annotation 依赖
out/ShizukuRoot.apk       构建产物
```

## 依赖

- Shizuku API 13.1.5（Apache-2.0，RikkaApps）
- GhostLock / FNE 漏洞利用工具链（来自设备第三方 root 应用 assets，仅作个人复现记录）
- KernelSU 内核模块 32601 / ksud 3.3.0

## 已知限制

- 漏洞成功率不稳定（实测多次尝试中 W1 成功、W2 崩溃居多），每次失败可能 panic 重启
- 崩溃可能损坏文件系统，导致权限重置、应用数据损坏
- 不保证通过 Play Integrity / 设备认证
- 不提供开机自启或持久化

## 许可

本项目源码采用 Apache-2.0；第三方组件归各自权利人所有。本项目不代表 HONOR 或 KernelSU 官方。
