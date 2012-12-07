package org.jetbrains.jps.cmdline;

import java.lang.reflect.Method;

/**
 * @author Eugene Zhuravlev
 *         Date: 12/6/12
 */
class ProfilingHelper {

  private final Class<?> myControllerClass;
  private final Object myController;

  ProfilingHelper() throws Exception {
    myControllerClass = Class.forName("com.yourkit.api.Controller");
    myController = myControllerClass.newInstance();
  }

  public void startProfiling() {
    try {
      final Method startMethod = myControllerClass.getDeclaredMethod("startCPUProfiling", long.class, String.class);
      if (startMethod != null) {
        startMethod.invoke(myController, 4L/*ProfilingModes.CPU_SAMPLING*/, null);
      }
      else {
        System.err.println("Cannot find method 'startCPUProfiling' in class " + myControllerClass.getName());
      }
    }
    catch (Throwable e) {
      e.printStackTrace();
    }
  }

  public void stopProfiling() {
    try {
      final Method captureMethod = myControllerClass.getDeclaredMethod("captureSnapshot", long.class);
      if (captureMethod != null) {
        final String path = (String)captureMethod.invoke(myController, 0L/*ProfilingModes.SNAPSHOT_WITHOUT_HEAP*/);
        System.err.println("CPU Snapshot captured: " + path);
        final Method stopMethod = myControllerClass.getDeclaredMethod("stopCPUProfiling");
        if (stopMethod != null) {
          stopMethod.invoke(myController);
        }
        else {
          System.err.println("Cannot find method 'stopCPUProfiling' in class " + myControllerClass.getName());
        }
      }
      else {
        System.err.println("Cannot find method 'captureSnapshot' in class " + myControllerClass.getName());
      }
    }
    catch (Throwable e) {
      e.printStackTrace();
    }
  }
}