package net.froemling.bsremote;

public class ObjRunnable implements Runnable {
  public String obj;

  protected ObjRunnable(String objIn) {
    obj = objIn;
  }

  public void run() {}
}
