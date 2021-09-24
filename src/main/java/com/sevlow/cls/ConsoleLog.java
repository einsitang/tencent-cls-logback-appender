package com.sevlow.cls;

/**
 * 控制台打印
 *
 * @author einsitang
 */
public class ConsoleLog {

  private final String name;

  private final boolean isDebug;

  public ConsoleLog(String name, boolean isDebug) {
    this.name = name;
    this.isDebug = isDebug;
  }

  public void log(String message) {
    if (this.isDebug) {
      System.out.println("[" + name + "] ".concat(message));
    }
  }
}
