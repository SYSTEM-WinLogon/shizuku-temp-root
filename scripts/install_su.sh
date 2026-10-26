#!/system/bin/sh
D=/data/local/tmp/ksu_fne
echo "=== INSTALL SU START ==="
id
getenforce
echo "--- ksu bin before ---"
ls -l /data/adb/ksu/bin 2>&1
echo "--- ksud install ---"
$D/ksud install 2>&1
echo "install rc=$?"
echo "--- ksu bin after ---"
ls -l /data/adb/ksu/bin 2>&1
echo "--- su entries ---"
ls -l /system/bin/su /data/adb/ksu/bin/su 2>&1
echo "--- su test ---"
/data/adb/ksu/bin/su -c id 2>&1 | head -3
echo "=== INSTALL SU DONE ==="
