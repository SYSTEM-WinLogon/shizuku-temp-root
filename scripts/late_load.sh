#!/system/bin/sh
D=/data/local/tmp/ksu_fne
echo "=== LATE LOAD START ==="
id
getenforce
echo "--- ksud version ---"
$D/ksud --version 2>&1
echo "--- late-load ---"
$D/ksud late-load --allow-shell --kmi 5.4 2>&1
echo "late-load rc=$?"
sleep 3
echo "--- su entries ---"
ls -l /system/bin/su /data/adb/ksu/bin/su 2>&1
echo "--- su test (bin) ---"
/data/adb/ksu/bin/su -c id 2>&1 | head -3
echo "--- su test (system) ---"
su -c id 2>&1 | head -3
echo "=== LATE LOAD DONE ==="
