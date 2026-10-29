package com.smr.rootapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
  private static final String D = "/data/local/tmp/ksu_fne";
  private static final int REQ = 1001;

  private final ExecutorService work = Executors.newSingleThreadExecutor();
  private final Handler ui = new Handler(Looper.getMainLooper());
  private TextView state, log;
  private Button rootBtn, authBtn, checkBtn, cmdBtn, enforceBtn;
  private volatile boolean busy, dead;

  private int dp(int n) { return (int) (getResources().getDisplayMetrics().density * n); }

  private TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    return t;
  }

  private GradientDrawable card(int color) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(18));
    return d;
  }

  private void append(String s) {
    ui.post(() -> { if (!dead) log.append(s + "\n"); });
  }

  private void setState(String s) {
    ui.post(() -> { if (!dead) state.setText(s); });
  }

  private void setBusy(boolean b) {
    busy = b;
    ui.post(() -> {
      if (dead) return;
      rootBtn.setEnabled(!b);
      authBtn.setEnabled(!b);
      checkBtn.setEnabled(!b);
      cmdBtn.setEnabled(!b);
      enforceBtn.setEnabled(!b);
    });
  }

  private Button button(LinearLayout parent, String title, boolean primary) {
    Button b = new Button(this);
    b.setText(title);
    b.setAllCaps(false);
    b.setTextSize(16);
    b.setTextColor(primary ? Color.WHITE : Color.rgb(22, 80, 65));
    b.setBackground(card(primary ? Color.rgb(22, 107, 84) : Color.rgb(226, 237, 231)));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
    lp.topMargin = dp(12);
    parent.addView(b, lp);
    return b;
  }

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().setStatusBarColor(Color.rgb(246, 248, 244));
    getWindow().setNavigationBarColor(Color.rgb(246, 248, 244));
    getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

    ScrollView scroll = new ScrollView(this);
    scroll.setBackgroundColor(Color.rgb(246, 248, 244));
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(24), dp(28), dp(24), dp(32));
    scroll.addView(body);
    setContentView(scroll);

    body.addView(text("Shizuku · 无线 ADB", 13, Color.rgb(22, 107, 84)));
    TextView title = text("临时 Root", 30, Color.rgb(22, 36, 30));
    title.setTypeface(null, Typeface.BOLD);
    title.setPadding(0, dp(10), 0, dp(12));
    body.addView(title);
    body.addView(text("通过 Shizuku 的 shell 权限，在手机上执行 GhostLock / FNE 提权，无需电脑。", 16, Color.rgb(79, 93, 84)));

    state = text("尚未连接 Shizuku。请先安装并启动 Shizuku（无线调试），再回到这里授权。", 16, Color.rgb(22, 64, 49));
    state.setPadding(dp(18), dp(18), dp(18), dp(18));
    state.setBackground(card(Color.rgb(229, 239, 232)));
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
    sp.topMargin = dp(22);
    sp.bottomMargin = dp(8);
    body.addView(state, sp);

    authBtn = button(body, "授权 Shizuku", false);
    checkBtn = button(body, "检查状态", false);
    cmdBtn = button(body, "执行命令", false);
    enforceBtn = button(body, "恢复 SELinux 到 Enforcing", false);
    rootBtn = button(body, "一键获取 Root", true);

    TextView note = text(
        "流程：释放工具链 → 触发内核漏洞（可能 panic 重启）→ 建立 root 通道。"
        + "漏洞是概率性的，失败重启后重跑即可；重启会恢复 SELinux。"
        + "本 App 不写入系统分区，root 仅在本次开机有效。", 13, Color.rgb(92, 101, 94));
    note.setPadding(0, dp(20), 0, dp(18));
    body.addView(note);

    log = text("", 12, Color.rgb(74, 84, 78));
    log.setTextIsSelectable(true);
    log.setTypeface(Typeface.MONOSPACE);
    body.addView(log);

    authBtn.setOnClickListener(v -> doAuth());
    checkBtn.setOnClickListener(v -> execute(this::checkStatus));
    rootBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
        .setTitle("获取临时 Root")
        .setMessage("将执行 GhostLock / FNE 内核漏洞利用。过程中可能触发内核 panic 并重启手机，"
            + "重启后需要重新运行。请保存未完成的工作。")
        .setNegativeButton("取消", null)
        .setPositiveButton("开始", (d, w) -> execute(this::doRoot))
        .show());
    cmdBtn.setOnClickListener(v -> {
      EditText input = new EditText(this);
      input.setHint("例如：id 或 ls /data/adb");
      new AlertDialog.Builder(this)
          .setTitle("以 root 执行命令")
          .setView(input)
          .setNegativeButton("取消", null)
          .setPositiveButton("执行", (d, w) -> {
            String c = input.getText().toString().trim();
            if (!c.isEmpty()) execute(() -> runRootCmd(c));
          })
          .show();
    });
    enforceBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
        .setTitle("恢复 SELinux")
        .setMessage("通过 root 通道把 SELinux 切回 Enforcing，root 通道可能随之失效。")
        .setNegativeButton("取消", null)
        .setPositiveButton("执行", (d, w) -> execute(() -> {
          runRootCmd("echo 1 > /sys/fs/selinux/enforce; getenforce");
        }))
        .show());

    // 启动时不触碰 Shizuku，避免未安装/未运行时初始化异常导致闪退。
    setState("请先安装并启动 Shizuku（无线调试），然后点「授权 Shizuku」。");
  }

  private boolean alive() {
    return Shizuku.pingBinder();
  }

  private void requirePermission() throws IOException {
    if (!alive()) throw new IOException("Shizuku 未运行，请先在 Shizuku 中通过无线调试启动服务。");
    if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
      throw new IOException("尚未获得 Shizuku 授权，请点击「授权 Shizuku」。");
    }
  }

  private boolean listenersReady;

  private void ensureListeners() {
    if (listenersReady) return;
    listenersReady = true;
    Shizuku.addRequestPermissionResultListener((requestCode, grantResult) -> {
      if (requestCode == REQ) {
        ui.post(() -> setState(grantResult == PackageManager.PERMISSION_GRANTED
            ? "Shizuku 权限已授予，可以开始。"
            : "Shizuku 权限被拒绝，请重新授权或在 Shizuku 中手动允许。"));
      }
    });
    Shizuku.addBinderDeadListener(() -> ui.post(() -> setState("Shizuku 服务已断开，请重新启动 Shizuku。")));
  }

  private void doAuth() {
    try {
      ensureListeners();
      if (!alive()) {
        setState("Shizuku 未运行。请先打开 Shizuku，按提示用无线调试启动服务。");
        return;
      }
      if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
        setState("Shizuku 权限已授予。");
        return;
      }
      Shizuku.requestPermission(REQ);
      setState("已在 Shizuku 中发起授权请求，请到手机上确认。");
    } catch (Throwable t) {
      setState("Shizuku 调用失败：" + t);
      append(t.toString());
    }
  }

  private static java.lang.reflect.Method NEW_PROCESS;
  static {
    try {
      NEW_PROCESS = Shizuku.class.getDeclaredMethod("newProcess",
          String[].class, String[].class, String.class);
      NEW_PROCESS.setAccessible(true);
    } catch (Throwable ignored) {
    }
  }

  private Process sh(String cmd) throws Exception {
    if (NEW_PROCESS == null) throw new IOException("Shizuku API 不兼容（找不到 newProcess）");
    try {
      return (Process) NEW_PROCESS.invoke(null, new String[]{"/system/bin/sh", "-c", cmd}, null, null);
    } catch (java.lang.reflect.InvocationTargetException ite) {
      Throwable c = ite.getCause();
      if (c instanceof Exception) throw (Exception) c;
      throw new IOException("Shizuku 执行失败：" + c, c);
    }
  }

  private Thread drain(InputStream in, ByteArrayOutputStream out) {
    Thread t = new Thread(() -> {
      try {
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) {
          synchronized (out) { if (out.size() < 262144) out.write(b, 0, n); }
        }
      } catch (IOException ignored) {
      }
    });
    t.start();
    return t;
  }

  /**
   * Shizuku 的 ShizukuRemoteProcess.exitValue() 在进程未结束时抛 IllegalArgumentException
   * （不是标准的 IllegalThreadStateException），所以不能用 Process.waitFor(timeout)。
   * 这里自己轮询，IllegalThreadStateException 是其子类，一并被捕获。
   */
  private boolean await(Process p, int seconds) throws InterruptedException {
    long deadline = System.currentTimeMillis() + seconds * 1000L;
    while (System.currentTimeMillis() < deadline) {
      try {
        p.exitValue();
        return true;
      } catch (IllegalArgumentException e) {
        Thread.sleep(100);
      }
    }
    return false;
  }

  private String run(String cmd, int seconds) throws Exception {
    requirePermission();
    Process p = sh(cmd);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    Thread t1 = drain(p.getInputStream(), out);
    Thread t2 = drain(p.getErrorStream(), err);
    if (!await(p, seconds)) {
      p.destroy();
      throw new IOException("命令超时：" + cmd);
    }
    t1.join(2000);
    t2.join(2000);
    String s, e;
    synchronized (out) { s = out.toString("UTF-8"); }
    synchronized (err) { e = err.toString("UTF-8"); }
    if (p.exitValue() != 0) throw new IOException("命令失败(" + p.exitValue() + ")：\n" + s + e);
    return s.trim();
  }

  private String runWithStdin(String cmd, InputStream data, int seconds) throws Exception {
    requirePermission();
    Process p = sh(cmd);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    Thread t1 = drain(p.getInputStream(), out);
    Thread t2 = drain(p.getErrorStream(), err);
    try (OutputStream os = p.getOutputStream()) {
      byte[] b = new byte[65536];
      int n;
      while ((n = data.read(b)) != -1) os.write(b, 0, n);
    } catch (IOException ignored) {
    }
    if (!await(p, seconds)) {
      p.destroy();
      throw new IOException("传输超时：" + cmd);
    }
    t1.join(2000);
    t2.join(2000);
    String e;
    synchronized (err) { e = err.toString("UTF-8"); }
    if (p.exitValue() != 0) throw new IOException("传输失败(" + p.exitValue() + ")：\n" + e);
    return "ok";
  }

  private void deployPayload() throws Exception {
    // 每次都覆盖释放：避免设备上残留旧版脚本（旧版 chain_hook 会把 SELinux 切回 Enforcing）
    setState("正在释放/更新工具链（约 20 MB）…");
    try (InputStream in = getAssets().open("payload.bin")) {
      runWithStdin("mkdir -p " + D + " && gzip -dc | tar -x -C /data/local/tmp", in, 300);
    }
    run("cd " + D + " && chmod 755 e ksud magiskpolicy fne_local.sh chain_hook.sh ksu_lateload.sh restore_enforce.sh ghostlock_root.sh 2>/dev/null", 20);
    // 覆盖设备上可能残留的旧 fneroot hook：旧版检查 /data/adb/ksu/bin/ksud 找不到就放弃加载模块
    run("cp " + D + "/ghostlock_root.sh /data/local/tmp/.ghostlock_root.sh 2>/dev/null; chmod 755 /data/local/tmp/.ghostlock_root.sh 2>/dev/null", 15);
    String chk = run("test -x " + D + "/e && test -f " + D + "/chain_hook.sh && echo ready", 10);
    if (!chk.contains("ready")) throw new IOException("工具链释放失败");
  }

  private void doRoot() {
    try {
      requirePermission();
      String kernel = run("uname -r 2>/dev/null", 10).trim();
      if (!kernel.contains("5.4.289-qgki-gf6cb1f712a6e")) {
        final String k = kernel;
        ui.post(() -> {
          if (dead) return;
          new AlertDialog.Builder(this)
              .setTitle("内核不匹配")
              .setMessage("当前内核：" + k + "\n\n本工具仅适配 5.4.289-qgki-gf6cb1f712a6e。\n系统更新（OTA）更换内核后漏洞会失效，继续可能白跑或崩溃。")
              .setNegativeButton("取消", null)
              .setPositiveButton("仍要继续", (d, w) -> execute(this::doRootForce))
              .show();
        });
        return;
      }
      doRootForce();
    } catch (Exception e) {
      setState("未完成：" + e.getMessage());
      append(e.toString());
    }
  }

  private void doRootForce() {
    try {
      deployPayload();
      run("mkdir -p /storage/emulated/0/root/_fne", 10);
      setState("正在启动漏洞利用流程…");
      String start = run("cd " + D + " && rm -f app_root.log && nohup setsid sh fne_local.sh > app_root.log 2>&1 < /dev/null & echo started", 20);
      append(start);
      pollRoot();
    } catch (Exception e) {
      setState("未完成：" + e.getMessage());
      append(e.toString());
    }
  }

  private void pollRoot() {
    for (int i = 0; i < 300 && !dead; i++) {
      try {
        Thread.sleep(2000);
        String s = run("cat " + D + "/app_root.log 2>/dev/null | tail -n 3; echo '###'; "
            + "cat " + D + "/chain_status 2>/dev/null; echo '###'; "
            + "grep kernelsu /proc/modules 2>/dev/null | head -1", 15);
        setState(s.isEmpty() ? "等待设备端输出…" : s);
        if (s.contains("kernelsu") || s.contains("KernelSU loaded") || s.contains("ROOT SHELL CHANNEL UP")) {
          append(s);
          setState("Root 就绪，正在收尾…");
          loadKsu();
          return;
        }
        if (s.contains("流程结束") || s.contains("没有建立 root 通道")) {
          append(s);
          return;
        }
      } catch (Exception e) {
        append("轮询失败：" + e.getMessage());
        return;
      }
    }
    setState("等待超时。请点「检查状态」查看日志。");
  }

  private void checkStatus() {
    try {
      String s = run("echo '--- 工具链 ---'; ls -l " + D + "/e 2>/dev/null || echo 未释放; "
          + "echo '--- 进程 ---'; ps -A -o PID,NAME 2>/dev/null | grep -E 'fne|rs.in' | grep -v grep || echo 无; "
          + "echo '--- 通道 ---'; cat /data/local/tmp/rs.out 2>/dev/null | tail -n 3 || echo 无; "
          + "echo '--- 流程日志 ---'; cat " + D + "/app_root.log 2>/dev/null | tail -n 6; "
          + "echo '--- SELinux ---'; getenforce; "
          + "echo '--- su ---'; ls -l /system/bin/su 2>/dev/null || echo 无; /system/bin/su -c id 2>&1 | head -1", 20);
      setState(s);
      append(s);
    } catch (Exception e) {
      setState("检查失败：" + e.getMessage());
    }
  }

  private void loadKsu() {
    try {
      // 新版 chain_hook 在 slide mismatch 时会自己 ksud insmod；旧流程则通过通道补
      String mod = run("grep kernelsu /proc/modules 2>/dev/null || echo no-ksu", 15);
      if (mod.contains("no-ksu")) {
        setState("模块未加载，正在通过 root 通道加载 KernelSU…");
        String b64 = Base64.encodeToString("sh /data/local/tmp/ksu_fne/ksu_lateload.sh".getBytes("UTF-8"), Base64.NO_WRAP);
        run("timeout 10 sh -c 'printf %s " + b64 + " | base64 -d > /data/local/tmp/rs.in'", 20);
        for (int i = 0; i < 30 && !dead; i++) {
          Thread.sleep(2000);
          mod = run("grep kernelsu /proc/modules 2>/dev/null || echo no-ksu", 15);
          if (!mod.contains("no-ksu")) break;
          setState("等待 KernelSU 模块加载…");
        }
      }
      if (mod.contains("no-ksu")) {
        setState("KernelSU 模块未加载成功。请点「检查状态」查看日志。");
        return;
      }
      append(mod);
      // 该模块的 su 只在 Permissive 下可用；late-load 挂载 su 需要几秒，轮询等待
      setState("模块已加载，等待 su 就绪…");
      String su = "";
      for (int i = 0; i < 20 && !dead; i++) {
        su = run("ls -l /system/bin/su 2>/dev/null; /system/bin/su -c id 2>&1 | head -1", 20);
        if (su.contains("uid=0")) break;
        Thread.sleep(2000);
      }
      append(su);
      if (su.contains("uid=0")) {
        setState("完成：临时 Root 可用（Permissive）。\nsu -c id = uid=0(root)\n打开 KernelSU 管理器即可使用。");
      } else {
        setState("模块已加载，但 su 未就绪。\n保持 Permissive 后再点「检查状态」。\n" + su);
      }
    } catch (Exception e) {
      setState("加载 KernelSU 失败：" + e.getMessage());
      append(e.toString());
    }
  }

  private void runRootCmd(String cmd) {
    try {
      String b64 = Base64.encodeToString(cmd.getBytes("UTF-8"), Base64.NO_WRAP);
      run("timeout 8 sh -c 'printf %s " + b64 + " | base64 -d > /data/local/tmp/rs.in'", 20);
      Thread.sleep(1000);
      String out = run("tail -n 40 /data/local/tmp/rs.out 2>/dev/null", 15);
      append(out);
      setState("命令已发送到 root 通道。");
    } catch (Exception e) {
      setState("执行失败：" + e.getMessage());
      append(e.toString());
    }
  }

  private void execute(Runnable task) {
    if (busy) return;
    setBusy(true);
    work.execute(() -> {
      try {
        task.run();
      } catch (Throwable e) {
        setState("未完成：" + e);
        append(e.toString());
      } finally {
        setBusy(false);
      }
    });
  }

  @Override
  protected void onDestroy() {
    dead = true;
    ui.removeCallbacksAndMessages(null);
    work.shutdown();
    super.onDestroy();
  }
}
