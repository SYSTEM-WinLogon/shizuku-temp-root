#!/system/bin/sh
# 由漏洞进程 e 在提权成功后执行。原版 fneroot hook 会检查 /data/adb/ksu/bin/ksud
# 并因找不到而放弃加载 KernelSU；这里改为直接启动 chain_hook（自动 ksud insmod）。
D=/data/local/tmp/ksu_fne
echo "[+] ghostlock hook pid=$$ uid=$(id -u) ctx=$(cat /proc/self/attr/current 2>/dev/null)"

# 兼容旧查找路径：把我们的 ksud 放到 fneroot 期望的位置
mkdir -p /data/adb/ksu/bin 2>/dev/null
if [ -x "$D/ksud" ]; then
  cp "$D/ksud" /data/adb/ksu/bin/ksud 2>/dev/null
  chmod 755 /data/adb/ksu/bin/ksud 2>/dev/null
fi

# 启动 chain_hook：它会 insmod 模块，并在 slide mismatch 时回退到 ksud insmod
if [ -x "$D/chain_hook.sh" ]; then
  echo "[*] launching ksu_fne chain_hook"
  /system/bin/setsid /system/bin/sh "$D/chain_hook.sh" </dev/null >>"$D/hook_launch.log" 2>&1 &
fi

# 保持 Permissive：该模块的 su 只在 Permissive 下可用
echo "[*] keeping permissive"
