package com.smr.rootapp;

import android.app.Application;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Date;

public class App extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
      try {
        File dir = getExternalFilesDir(null);
        if (dir == null) dir = getFilesDir();
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, "crash.txt");
        try (PrintWriter w = new PrintWriter(new FileWriter(f, true))) {
          w.println("=== " + new Date() + " ===");
          e.printStackTrace(w);
          w.flush();
        }
      } catch (Throwable ignored) {
      }
      if (prev != null) {
        prev.uncaughtException(t, e);
      } else {
        android.os.Process.killProcess(android.os.Process.myPid());
      }
    });
  }
}
