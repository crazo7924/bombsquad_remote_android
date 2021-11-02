package net.froemling.bsremote.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import net.froemling.bsremote.LogThread;
import net.froemling.bsremote.ObjRunnable;
import net.froemling.bsremote.R;
import net.froemling.bsremote.ServerEntry;
import net.froemling.bsremote.WorkerThread;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

public class ScanActivity extends Activity {

  public static final boolean debug = false;
  public static final String TAG = "BSRemoteScan";
  static final int BS_PACKET_REMOTE_GAME_QUERY = 8;
  static final int BS_PACKET_REMOTE_GAME_RESPONSE = 9;
  static String playerName = "The Dude";
  protected ListView listView;
  protected LibraryAdapter adapter;
  Map<String, ServerEntry> serverEntries;
  private Timer processTimer;
  private WorkerThread scannerThread;
  private WorkerThread stopperThread;
  private WorkerThread readThread;
  private DatagramSocket scannerSocket;
  private long lastGameClickTime = 0;

  public static String getPlayerName() {
    return playerName;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    if (debug) {
      Log.v(TAG, "onCreate()");
    }
    super.onCreate(savedInstanceState);

    // we want to use our longer title here but we cant set it in the
    // manifest since it carries over to our icon; grumble.
    setTitle(R.string.app_name);

    setContentView(R.layout.gen_list);

    scannerThread = new WorkerThread();
    scannerThread.start();

    stopperThread = new WorkerThread();
    stopperThread.start();

    this.adapter = new LibraryAdapter(this, this);
    this.listView = this.findViewById(android.R.id.list);
    this.listView.addHeaderView(adapter.headerView, null, false);
    this.listView.setAdapter(adapter);

    // pull our player name from prefs
    final SharedPreferences preferences =
        getSharedPreferences("BSRemotePrefs", Context.MODE_PRIVATE);
    String playerNameVal = preferences.getString("playerName", "");
    assert playerNameVal != null;
    if (!playerNameVal.equals("")) {
      playerName = playerNameVal;
    }

    EditText editText = adapter.headerView.findViewById(R.id.nameEditText);
    if (editText != null) {

      // hmmm this counts emoji as 2 still but whatever...
      editText.setFilters(new InputFilter[] {new InputFilter.LengthFilter(10)});

      editText.setText(getPlayerName());
      editText.addTextChangedListener(
          new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
              playerName = s.toString();
              SharedPreferences.Editor editor = preferences.edit();
              editor.putString("playerName", playerName);
              editor.apply();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {}
          });
    }

    listView.setOnItemClickListener(
        (parent, view, position, id) -> {
          String name = ((TextView) view.findViewById(android.R.id.text1)).getText().toString();

          scannerThread.doRunnable(
              new ObjRunnable(name) {
                public void run() {

                  // prevent case where double tapping an entry quickly enough can
                  // bring up two gamepad activities
                  long currentTime = SystemClock.uptimeMillis();
                  if (currentTime - ScanActivity.this.lastGameClickTime < 2000) {
                    Log.v(TAG, "Suppressing repeat join-game tap.");
                    return;
                  }
                  ScanActivity.this.lastGameClickTime = currentTime;
                  if (serverEntries.containsKey(obj)) {
                    ServerEntry se = serverEntries.get(obj);
                    assert se != null;
                    Intent gamePadIntent = new Intent(ScanActivity.this, GamePadActivity.class);
                    gamePadIntent.putExtra("connectAddrs", new String[] {se.address.getHostName()});
                    gamePadIntent.putExtra("connectPort", se.port);
                    gamePadIntent.putExtra("newStyle", true);
                    startActivity(gamePadIntent);
                  }
                }
              });
        });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.act_scan, menu);

    return true;
  }

  @Override
  public void onDestroy() {
    if (debug) {
      Log.v(TAG, "onDestroy()");
    }
    super.onDestroy();
    // have the worker threads shut themselves down
    scannerThread.doRunnable(
        () -> {
          scannerThread.getLooper().quitSafely();
          scannerThread = null;

          // have the worker threads shut themselves down
          stopperThread.doRunnable(
              () -> {
                stopperThread.getLooper().quitSafely();
                stopperThread = null;
              });
        });
  }

  @Override
  public void onPause() {
    super.onPause();
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  @Override
  public void onStart() {
    if (debug) {
      Log.v(TAG, "onStart()");
    }
    super.onStart();

    adapter.knownGames.clear();
    adapter.notifyDataSetChanged();

    serverEntries = new HashMap<>();

    try {
      scannerSocket = new DatagramSocket();
      scannerSocket.setBroadcast(true);
    } catch (SocketException e1) {
      LogThread.log("Error setting up scanner socket", e1, this);
    }

    readThread = new WorkerThread();
    readThread.start();

    // all the read-thread does is wait for data to come in
    // and pass it to the process-thread
    readThread.doRunnable(
        () -> {
          while (true) {
            try {
              byte[] buf = new byte[256];
              DatagramPacket packet = new DatagramPacket(buf, buf.length);
              scannerSocket.receive(packet);
              abstract class PacketRunnable implements Runnable {
                final DatagramPacket p;

                PacketRunnable(DatagramPacket pIn) {
                  p = pIn;
                }

                public abstract void run();
              }
              scannerThread.doRunnable(
                  new PacketRunnable(packet) {
                    public void run() {

                      // if this is a game response packet...
                      if (p.getData().length > 1
                          && p.getData()[0] == BS_PACKET_REMOTE_GAME_RESPONSE) {
                        // extract name
                        String s = new String(Arrays.copyOfRange(p.getData(), 1, p.getLength()));

                        // if this one isnt on our list, add it and
                        // inform the ui of its existence
                        if (!serverEntries.containsKey(s)) {
                          serverEntries.put(s, new ServerEntry());
                          // hmm should we store its address only
                          // when adding or every time
                          // we hear from them?..
                          ServerEntry entry = serverEntries.get(s);
                          assert entry != null;
                          entry.address = p.getAddress();
                          entry.port = p.getPort();
                          runOnUiThread(
                              new ObjRunnable(s) {
                                public void run() {
                                  adapter.notifyFound(obj);
                                }
                              });
                        }
                        ServerEntry entry = serverEntries.get(s);
                        assert entry != null;
                        entry.lastPingTime = SystemClock.uptimeMillis();
                      }
                    }
                  });

            } catch (IOException e) {
              // assuming this means the socket is closed..
              readThread.getLooper().quitSafely();
              readThread = null;
              break;
            } catch (ArrayIndexOutOfBoundsException e) {
              LogThread.log("Got excessively sized datagram packet", e, ScanActivity.this);
            }
          }
        });

    // start our timer to send out query packets, etc
    processTimer = new Timer();
    processTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {

            // when this timer fires, tell our process thread to run
            scannerThread.doRunnable(
                () -> {

                  // send broadcast packets to all our network
                  // interfaces to find games
                  try {

                    byte[] sendData = new byte[1];
                    sendData[0] = BS_PACKET_REMOTE_GAME_QUERY;

                    Enumeration<NetworkInterface> interfaces =
                        NetworkInterface.getNetworkInterfaces();
                    while (interfaces.hasMoreElements()) {
                      NetworkInterface networkInterface = interfaces.nextElement();

                      if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                        continue;
                      }

                      for (InterfaceAddress interfaceAddress :
                          networkInterface.getInterfaceAddresses()) {
                        InetAddress broadcast = interfaceAddress.getBroadcast();
                        if (broadcast == null) {
                          continue;
                        }
                        try {
                          DatagramPacket sendPacket =
                              new DatagramPacket(sendData, sendData.length, broadcast, 43210);
                          scannerSocket.send(sendPacket);
                        } catch (Exception e) {
                          Log.v(TAG, "Broadcast datagram send " + "error");
                        }
                      }
                    }

                  } catch (IOException ex) {
                    LogThread.log("", ex, ScanActivity.this);
                  }

                  // prune servers we haven't heard from in a while..
                  long curTime = SystemClock.uptimeMillis();
                  Iterator<Entry<String, ServerEntry>> it = serverEntries.entrySet().iterator();
                  while (it.hasNext()) {
                    Entry<String, ServerEntry> thisEntry = it.next();
                    long age = curTime - thisEntry.getValue().lastPingTime;
                    if (age > 5000) {
                      runOnUiThread(
                          new ObjRunnable(thisEntry.getKey()) {
                            public void run() {
                              adapter.notifyLost(obj);
                            }
                          });
                      it.remove();
                    }
                  }
                });
          }
        },
        0,
        1000);
  }

  @Override
  public void onStop() {
    if (debug) {
      Log.v(TAG, "onStop()");
    }
    super.onStop();
    processTimer.cancel();
    processTimer.purge();
    scannerSocket.close();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    if (item.getItemId() == R.id.menu_search_by_ip) {

      // Creating the AlertDialog object
      final AlertDialog.Builder ipDialog = new AlertDialog.Builder(this);

      LayoutInflater layoutInflater =
          (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);

      @SuppressLint("InflateParams")
      View view = layoutInflater.inflate(R.layout.dialog, null);

      final SharedPreferences preferences =
          getSharedPreferences("BSRemotePrefs", Context.MODE_PRIVATE);
      String addrVal = preferences.getString("manualConnectAddress", "");

      // set up EditText to get input
      final EditText editText = view.findViewById(R.id.editText);
      editText.setText(addrVal);

      editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);

      // handle OK clicks
      ipDialog.setPositiveButton(
          R.string.connect,
          (arg0, arg1) -> {
            String ipFromUser = editText.getText().toString().trim();

            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("manualConnectAddress", ipFromUser);
            editor.apply();

            Intent myIntent = new Intent(ScanActivity.this, GamePadActivity.class);
            myIntent.putExtra("connectAddrs", new String[] {ipFromUser});
            myIntent.putExtra("connectPort", 43210);
            myIntent.putExtra("newStyle", true);
            startActivity(myIntent);
          });

      // handle cancel clicks
      ipDialog.setNegativeButton(
          R.string.cancel,
          (arg0, arg1) -> {
            // do nothing
          });
      ipDialog.setView(view);

      ipDialog.show();
      return true;
    }
    return false;
  }
}
