#!/system/bin/sh
D=/data/local/tmp/ksu_fne
echo "=== LOAD KSU $(date) ==="
id
getenforce
echo "--- files ---"
ls -l $D/kernelsu.ko $D/pristine_policy 2>&1
echo "--- module before ---"
grep kernelsu /proc/modules 2>/dev/null || echo no-ksu
echo "--- insmod ---"
$D/ksud insmod $D/kernelsu.ko "allow_shell=1 pristine_policy=$D/pristine_policy" 2>&1
echo "insmod rc=$?"
sleep 3
echo "--- module after ---"
grep kernelsu /proc/modules 2>/dev/null || echo no-ksu
echo "--- su ---"
ls -l /system/bin/su /data/adb/ksu/bin/su 2>&1
su -c id 2>&1 | head -2
echo "=== LOAD DONE ==="
