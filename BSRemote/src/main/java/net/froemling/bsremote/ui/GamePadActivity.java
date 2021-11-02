package net.froemling.bsremote.ui;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.SystemClock;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import net.froemling.bsremote.LogThread;
import net.froemling.bsremote.R;
import net.froemling.bsremote.WorkerThread;
import net.froemling.bsremote.ui.gl.MainGLSurfaceView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class GamePadActivity extends Activity {

  // flip this on for lots of log spewage to help diagnose oddities
  public static final boolean debug = false;
  public static final String TAG = "BSRemoteGamePad";
  public static final int BS_REMOTE_STATE_PUNCH = 1;
  public static final int BS_REMOTE_STATE_JUMP = 1 << 1;
  public static final int BS_REMOTE_STATE_THROW = 1 << 2;
  public static final int BS_REMOTE_STATE_BOMB = 1 << 3;
  public static final int BS_REMOTE_STATE_MENU = 1 << 4;
  public static final int BS_REMOTE_STATE2_MENU = 1;
  public static final int BS_REMOTE_STATE2_JUMP = 1 << 1;
  public static final int BS_REMOTE_STATE2_PUNCH = 1 << 2;
  public static final int BS_REMOTE_STATE2_THROW = 1 << 3;
  public static final int BS_REMOTE_STATE2_BOMB = 1 << 4;
  public static final int BS_REMOTE_STATE2_RUN = 1 << 5;
  static final int BS_REMOTE_ERROR_VERSION_MISMATCH = 0;
  static final int BS_REMOTE_ERROR_GAME_SHUTTING_DOWN = 1;
  static final int BS_REMOTE_ERROR_NOT_ACCEPTING_CONNECTIONS = 2;
  static final int REMOTE_MSG_ID_REQUEST = 2;
  static final int REMOTE_MSG_ID_RESPONSE = 3;
  static final int REMOTE_MSG_DISCONNECT = 4;
  static final int REMOTE_MSG_STATE = 5;
  static final int REMOTE_MSG_STATE_ACK = 6;
  static final int REMOTE_MSG_DISCONNECT_ACK = 7;
  static final int REMOTE_MSG_STATE2 = 10;
  public boolean mWindowIsFocused = false;
  public short _buttonStateV1 = 0;
  public short _buttonStateV2 = 0;
  public float _dPadStateH = 0.0f;
  public float _dPadStateV = 0.0f;
  long _shutDownStartTime;
  float _averageLag;
  TextView _lagMeter;
  int _uniqueID;
  short[] _statesV1;
  int[] _statesV2;
  float _currentLag;
  private WorkerThread _readThread;
  // (bits 6-10 are d-pad h-value and bits 11-15 are dpad v-value)
  private WorkerThread _processThread;
  private Timer _processTimer;
  private Timer _processUITimer;
  private Timer _shutDownTimer;
  private DatagramSocket _socket;
  private InetAddress _addr; // actual address we're talking to
  private InetAddress[] _addrs; // for initial scanning
  private boolean[] _addrsValid;
  private int _port;
  private int _requestID;
  private byte _id;
  private boolean _dead = false;
  private long _lastLagUpdateTime;
  private int _nextState;
  private boolean _connected = false;
  private boolean _shouldPrintConnected;
  private int _requestedState = 0;
  private boolean _shuttingDown = false;
  private long _lastNullStateTime = 0;
  private long[] _stateBirthTimes;

  @SuppressWarnings("MismatchedReadAndWriteOfArray")
  private long[] _stateLastSentTimes;

  private long _lastSentState = 0;
  private boolean _usingProtocolV2 = false;
  private MainGLSurfaceView mGLView;
  private String[] _addrsRaw;

  private boolean _newStyle;

  public static Charset getCharsetUTF8() {
    return StandardCharsets.UTF_8;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {

    // keep android from crashing due to our network use in the main thread
    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
    StrictMode.setThreadPolicy(policy);

    // keep the device awake while this activity is visible.
    // granted most of the time the user is tapping on it, but if they have
    // a hardware gamepad attached they might not be.
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    // clients use a random request ID to differentiate themselves
    // (probably is a better way to do this..)
    long currentTime = SystemClock.uptimeMillis();
    _requestID = (int) currentTime % 10000;
    _id = -1; // ain't got one yet
    _stateBirthTimes = new long[256];
    _stateLastSentTimes = new long[256];
    _statesV1 = new short[256];
    _statesV2 = new int[256];

    // if we reconnect we may get acks for states we didn't send..
    // so lets set everything to current time to avoid screwing up
    // our lag-meter
    long curTime = SystemClock.uptimeMillis();
    for (int i = 0; i < 256; i++) {
      _stateBirthTimes[i] = curTime;
      _stateLastSentTimes[i] = 0;
    }

    Bundle extras = getIntent().getExtras();
    if (extras != null) {
      _newStyle = extras.getBoolean("newStyle");
      _port = extras.getInt("connectPort");
      try {
        _socket = new DatagramSocket();
      } catch (SocketException e) {
        LogThread.log("Error setting up gamepad socket", e, this);
      }
      _addrsRaw = extras.getStringArray("connectAddrs");
    }

    // read or create our random unique ID; we tack this onto our
    // android device identifier just in case its not actually unique
    // (as apparently was the case with nexus-2 or some other device)

    SharedPreferences preferences = getSharedPreferences("BSRemotePrefs", Context.MODE_PRIVATE);
    _uniqueID = preferences.getInt("uniqueId", 0);
    if (_uniqueID == 0) {
      while (_uniqueID == 0) {
        _uniqueID = new Random().nextInt() & 0xFFFF;
      }
      SharedPreferences.Editor editor = preferences.edit();
      editor.putInt("uniqueId", _uniqueID);
      editor.apply();
    }

    _processThread = new WorkerThread();
    _processThread.start();

    _readThread = new WorkerThread();
    _readThread.start();

    // all the read-thread does is wait for data to come in
    // and pass it to the process-thread
    _readThread.doRunnable(
        () -> {
          while (true) {
            try {
              byte[] buf = new byte[10];
              DatagramPacket packet = new DatagramPacket(buf, buf.length);
              _socket.receive(packet);
              abstract class PacketRunnable implements Runnable {
                final DatagramPacket p;

                PacketRunnable(DatagramPacket pIn) {
                  p = pIn;
                }

                public abstract void run();
              }
              _processThread.doRunnable(
                  new PacketRunnable(packet) {
                    public void run() {
                      // (delay for testing disconnect race conditions)
                      //                try {
                      //                  Thread.sleep(1000);
                      //                } catch (InterruptedException e) {
                      //                  e.printStackTrace();
                      //                }
                      GamePadActivity.this._readFromSocket(p);
                    }
                  });

            } catch (IOException e) {
              // assuming this means the socket is closed..
              if (debug) {
                Log.v(TAG, "READ THREAD DYING");
              }
              _readThread.getLooper().quitSafely();
              _readThread = null;
              break;
            } catch (ArrayIndexOutOfBoundsException e) {
              LogThread.log("Got excessively sized datagram packet", e, GamePadActivity.this);
            }
          }
        });

    super.onCreate(savedInstanceState);

    // Create a GLSurfaceView instance and set it
    // as the ContentView for this Activity
    mGLView = new MainGLSurfaceView(this);

    ViewGroup mLayout = new RelativeLayout(this);
    mLayout.addView(mGLView);

    _lagMeter = new TextView(this);
    _lagMeter.setTextColor(0xFF00FF00);
    _lagMeter.setText("--");
    RelativeLayout.LayoutParams params =
        new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
    params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);

    params.bottomMargin = 20;
    mLayout.addView(_lagMeter, params);

    setContentView(mLayout);
  }

  public void shutDown() {

    if (debug) {
      Log.v(TAG, "SETTING _shuttingDown");
    }
    _shuttingDown = true;
    _shutDownStartTime = SystemClock.uptimeMillis();

    // Create our shutdown timer.. this will keep us
    // trying to disconnect cleanly with the server until
    // we get confirmation or we give up.
    // Tell our worker thread to start its update timer.
    _processThread.doRunnable(
        () -> {
          if (debug) {
            Log.v(TAG, "CREATING SHUTDOWN TIMER...");
          }
          assert (_shutDownTimer == null);
          _shutDownTimer = new Timer();
          _shutDownTimer.schedule(
              new TimerTask() {
                @Override
                public void run() {
                  // when this timer fires, tell our process thread to run
                  if (_processThread == null) {
                    Log.v(TAG, "Got null _processThread in runnable.");
                    return;
                  }
                  _processThread.doRunnable(GamePadActivity.this::_process);
                }
              },
              0,
              100);
        });

    // let our gl view clean up anything it needs to
    if (mGLView != null) {
      mGLView.onClosing();
    }
  }

  @Override
  public void onDestroy() {

    super.onDestroy();
    if (debug) {
      Log.v(TAG, "onDestroy()");
    }
    shutDown();
  }

  @Override
  protected void onStart() {

    super.onStart();
    if (debug) {
      Log.v(TAG, "GPA onStart()");
    }

    // tell our worker thread to start its update timer
    _processThread.doRunnable(
        () -> {
          // kick off an id request... (could just wait for the process
          // timer to do this)
          if (_id == -1) {
            GamePadActivity.this._sendIdRequest();
          }

          if (debug) {
            Log.v(TAG, "CREATING PROCESS TIMER..");
          }
          _processTimer = new Timer();
          _processTimer.schedule(
              new TimerTask() {
                @Override
                public void run() {
                  if (_processThread != null) {
                    // when this timer fires, tell our process thread to run
                    _processThread.doRunnable(GamePadActivity.this::_process);
                  }
                }
              },
              0,
              100);

          // lets also do some upkeep stuff at a slower pace..
          _processUITimer = new Timer();
          _processUITimer.schedule(
              new TimerTask() {
                @Override
                public void run() {
                  // when this timer fires, tell our process thread to run
                  runOnUiThread(GamePadActivity.this::_processUI);
                }
              },
              0,
              2500);
        });
  }

  protected void onStop() {
    super.onStop();

    _processThread.doRunnable(
        () -> {
          _processTimer.cancel();
          _processTimer.purge();
          _processUITimer.cancel();
          _processUITimer.purge();
        });
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    return super.onKeyDown(keyCode, event);
  }

  // handle periodic processing such as receipt re-requests
  protected void _processUI() {
    // eww as of android 4.4.2 there's several things that cause
    // immersive mode state to get lost.. (such as volume up/down buttons)
    // ...so for now lets force the issue
    if (mWindowIsFocused) {
      _setImmersiveMode();
    }
  }

  @TargetApi(19)
  private void _setImmersiveMode() {
    int vis =
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    if (mGLView.getSystemUiVisibility() != vis) {
      mGLView.setSystemUiVisibility(vis);
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    mWindowIsFocused = hasFocus;
    if (hasFocus) {
      _setImmersiveMode();
    }
  }

  public void sendDisconnectPacket() {
    if (_id != -1) {
      byte[] data = new byte[2];
      data[0] = REMOTE_MSG_DISCONNECT;
      data[1] = _id;
      try {
        _socket.send(new DatagramPacket(data, data.length, _addr, _port));
      } catch (IOException e) {
        LogThread.log("", e, GamePadActivity.this);
      }
    }
  }

  private void _process() {
    if (_processThread != null && Thread.currentThread() != _processThread) {
      Log.v(TAG, "Incorrect thread on _process");
      return;
      // throw new AssertionError("thread error");
    }

    // if any of these happen after we're dead totally ignore them
    if (_dead) {
      return;
    }

    long t = SystemClock.uptimeMillis();

    // if we're shutting down but are still connected, keep sending
    // disconnects out
    if (_shuttingDown) {

      boolean finishUsOff = false;

      // shoot off another disconnect notice
      // once we're officially disconnected we can die
      if (!_connected) {
        finishUsOff = true;
      } else {
        sendDisconnectPacket();
        // just give up after a short while
        if (t - _shutDownStartTime > 5000) {
          finishUsOff = true;
        }
      }
      if (finishUsOff && !_dead && _shutDownTimer != null) {
        _dead = true;
        // ok we're officially dead.. clear out our threads/timers/etc
        _shutDownTimer.cancel();
        _shutDownTimer.purge();
        _processThread.getLooper().quitSafely();
        _processThread = null;
        // this should kill our read-thread
        _socket.close();
        return;
      }
    }

    // if we've got states we haven't heard an ack for yet, keep shipping 'em
    // out
    int stateDiff = (_requestedState - _nextState) & 0xFF;

    // if they've requested a state we don't have yet, we don't need to
    // resend anything
    if (stateDiff < 128) {
      // .. however we wanna shoot states at the server every now and then
      // even if we have no new states,
      // to keep from timing out and such..
      if (t - _lastNullStateTime > 3000) {
        _doStateChange(true);
        _lastNullStateTime = t;
      }
    } else {
      // ok we've got at least one state we haven't heard confirmation for
      // yet.. lets ship 'em out..
      if (_usingProtocolV2) {
        _shipUnAckedStatesV2();
      } else {
        _shipUnAckedStatesV1();
      }
    }

    // if we don't have an ID yet, keep sending off those requests...
    if (_id == -1) {
      _sendIdRequest();
    }

    // update our lag meter every so often
    if (t - _lastLagUpdateTime > 2000) {
      float smoothing = 0.5f;
      _averageLag = smoothing * _averageLag + (1.0f - smoothing) * _currentLag;

      // lets show half of our the round-trip time as lag.. (the actual
      // delay
      // in-game is just our packets getting to them; not the round trip)
      runOnUiThread(
          () -> {
            if (_shouldPrintConnected) {
              _lagMeter.setTextColor(0xFF00FF00);
              _lagMeter.setText(R.string.connected);
              _shouldPrintConnected = false;

            } else if (_connected) {
              // convert from millisecs to seconds
              float val = (_averageLag * 0.5f) / 1000.0f;
              if (val < 0.1) {
                _lagMeter.setTextColor(0xFF88FF88);
              } else if (val < 0.2) {
                _lagMeter.setTextColor(0xFFFFB366);
              } else {
                _lagMeter.setTextColor(0xFFFF6666);
              }
              _lagMeter.setText(
                  String.format(getString(R.string.lag).replace("${SECONDS}", "%.2f"), val));
            } else {
              // connecting...
              _lagMeter.setTextColor(0xFFFF8800);
              _lagMeter.setText(R.string.connecting);
            }
          });
      _currentLag = 0.0f;
      _lastLagUpdateTime = t;
    }
  }

  private void _shipUnAckedStatesV1() {

    // if we don't have an id yet this is moot..
    if (_id == -1) {
      return;
    }

    long curTime = SystemClock.uptimeMillis();

    // ok we need to ship out everything from their last requested state
    // to our current state.. (clamping at a reasonable value)
    if (_id != -1) {

      int statesToSend = (_nextState - _requestedState) & 0xFF;
      if (statesToSend > 11) {
        statesToSend = 11;
      }
      if (statesToSend < 1) {
        return;
      }

      byte[] data = new byte[100];
      data[0] = REMOTE_MSG_STATE;
      data[1] = _id;
      data[2] = (byte) statesToSend; // number of states we have here

      int s = (_nextState - statesToSend) & 0xFF;
      if (debug) {
        Log.v(TAG, "SENDING " + statesToSend + " STATES FROM: " + s);
      }
      data[3] = (byte) (s & 0xFF); // starting index

      // pack em in
      int index = 4;
      for (int i = 0; i < statesToSend; i++) {
        data[index++] = (byte) (_statesV1[s] & 0xFF);
        data[index++] = (byte) (_statesV1[s] >> 8);
        _stateLastSentTimes[s] = curTime;
        s = (s + 1) % 256;
      }
      if (_connected) {
        try {
          _socket.send(new DatagramPacket(data, 4 + 2 * statesToSend, _addr, _port));
        } catch (IOException e) {

          // if anything went wrong here, assume the game just shut down
          runOnUiThread(
              () -> {
                String msg = getString(R.string.gameShutDown);
                Context context = getApplicationContext();
                int duration = Toast.LENGTH_LONG;
                Toast toast = Toast.makeText(context, msg, duration);
                toast.show();
              });
          _connected = false;
          finish();
        }
      }
    }
  }

  private void _shipUnAckedStatesV2() {

    // if we don't have an id yet this is moot..
    if (_id == -1) {
      return;
    }

    long curTime = SystemClock.uptimeMillis();

    // ok we need to ship out everything from their last requested state
    // to our current state.. (clamping at a reasonable value)
    if (_id != -1) {

      int statesToSend = (_nextState - _requestedState) & 0xFF;
      if (statesToSend > 11) {
        statesToSend = 11;
      }
      if (statesToSend < 1) {
        return;
      }

      byte[] data = new byte[150];
      data[0] = REMOTE_MSG_STATE2;
      data[1] = _id;
      data[2] = (byte) statesToSend; // number of states we have here

      int s = (_nextState - statesToSend) & 0xFF;
      if (debug) {
        Log.v(TAG, "SENDING " + statesToSend + " STATES FROM: " + s);
      }
      data[3] = (byte) (s & 0xFF); // starting index

      // pack em in
      int index = 4;
      for (int i = 0; i < statesToSend; i++) {
        data[index++] = (byte) (_statesV2[s] & 0xFF);
        data[index++] = (byte) (_statesV2[s] >> 8);
        data[index++] = (byte) (_statesV2[s] >> 16);
        _stateLastSentTimes[s] = curTime;
        s = (s + 1) % 256;
      }
      if (_connected) {
        try {
          _socket.send(new DatagramPacket(data, 4 + 3 * statesToSend, _addr, _port));
        } catch (IOException e) {

          // if anything went wrong here, assume the game just shut down
          runOnUiThread(
              () -> {
                String msg = getString(R.string.gameShutDown);
                Context context = getApplicationContext();
                int duration = Toast.LENGTH_LONG;
                Toast toast = Toast.makeText(context, msg, duration);
                toast.show();
              });
          _connected = false;
          finish();
        }
      }
    }
  }

  public void _doStateChange(boolean force) {
    if (_usingProtocolV2) {
      _doStateChangeV2(force);
    } else {
      _doStateChangeV1(force);
    }
  }

  public void _doStateChangeV2(boolean force) {

    // compile our state value
    int s = _buttonStateV2; // buttons
    int hVal = (int) (256.0f * (0.5f + _dPadStateH * 0.5f));
    if (hVal < 0) {
      hVal = 0;
    } else if (hVal > 255) {
      hVal = 255;
    }

    int vVal = (int) (256.0f * (0.5f + _dPadStateV * 0.5f));
    if (vVal < 0) {
      vVal = 0;
    } else if (vVal > 255) {
      vVal = 255;
    }

    s |= hVal << 8;
    s |= vVal << 16;

    // if our compiled state value hasn't changed, don't send.
    // (analog joystick noise can send a bunch of redundant states through
    // here)
    // The exception is if forced is true, which is the case with packets
    // that double as keepalives.
    if ((s == _lastSentState) && (!force)) {
      return;
    }

    _stateBirthTimes[_nextState] = SystemClock.uptimeMillis();
    _stateLastSentTimes[_nextState] = 0;

    if (debug) {
      Log.v(TAG, "STORING NEXT STATE: " + _nextState);
    }
    _statesV2[_nextState] = s;
    _nextState = (_nextState + 1) % 256;
    _lastSentState = s;

    // if we're pretty up to date as far as state acks, lets go ahead
    // and send out this state immediately..
    // (keeps us nice and responsive on low latency networks)
    int unackedCount = (_nextState - _requestedState) & 0xFF; // upcast to
    // get unsigned
    if (unackedCount < 3) {
      _shipUnAckedStatesV2();
    }
  }

  void _doStateChangeV1(boolean force) {

    // compile our state value
    short s = _buttonStateV1; // buttons
    s |= (_dPadStateH > 0 ? 1 : 0) << 5; // sign bit
    s |= ((int) (Math.round(Math.min(1.0, Math.abs(_dPadStateH)) * 15.0))) << 6; // mag
    s |= (_dPadStateV > 0 ? 1 : 0) << 10; // sign bit
    s |= ((int) (Math.round(Math.min(1.0, Math.abs(_dPadStateV)) * 15.0))) << 11; // mag

    // if our compiled state value hasn't changed, don't send.
    // (analog joystick noise can send a bunch of redundant states through here)
    // The exception is if forced is true, which is the case with packets that
    // double as keepalives.
    if ((s == _lastSentState) && (!force)) {
      return;
    }

    _stateBirthTimes[_nextState] = SystemClock.uptimeMillis();
    _stateLastSentTimes[_nextState] = 0;

    if (debug) {
      Log.v(TAG, "STORING NEXT STATE: " + _nextState);
    }
    _statesV1[_nextState] = s;
    _nextState = (_nextState + 1) % 256;
    _lastSentState = s;

    // if we're pretty up to date as far as state acks, lets go ahead
    // and send out this state immediately..
    // (keeps us nice and responsive on low latency networks)
    int unackedCount = (_nextState - _requestedState) & 0xFF; // upcast to
    // get
    // unsigned
    if (unackedCount < 3) {
      _shipUnAckedStatesV1();
    }
  }

  private void _sendIdRequest() {
    if (_connected) {
      throw new AssertionError();
    }
    if (_id != -1) {
      throw new AssertionError();
    }
    if (Thread.currentThread() != _processThread) {
      throw new AssertionError();
    }

    // get our unique identifier to tack onto the end of our name
    // (so if we drop our connection and reconnect we can be reunited with
    // our old
    // dude instead of leaving a zombie)
    @SuppressLint("HardwareIds")
    String android_id =
        Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID);

    // on new-style connections we include unique id info in our name so we
    // can be re-connected
    // if we disconnect
    String deviceName;
    if (_newStyle) {
      String name = ScanActivity.getPlayerName();
      // make sure we don't have any #s in it
      name = name.replaceAll("#", "");
      deviceName = name + "#" + android_id + _uniqueID;

    } else {
      deviceName = "Android remote (" + android.os.Build.MODEL + ")";
    }
    byte[] nameBytes;
    nameBytes = deviceName.getBytes(getCharsetUTF8());
    int dLen = nameBytes.length;
    if (dLen > 99) {
      dLen = 99;
    }

    // send a hello on all our addrs
    // Log.v(TAG, "Sending ID Request");
    byte[] data = new byte[128];
    data[0] = REMOTE_MSG_ID_REQUEST;
    data[1] = 121; // old protocol version..
    data[2] = (byte) (_requestID & 0xFF);
    data[3] = (byte) (_requestID >> 8);
    data[4] = 50; // protocol version request (this implies we support
    // state-packet-2 if they do)
    int len = 5;
    for (int i = 0; i < dLen; i++) {
      data[5 + i] = nameBytes[i];
      len++;
    }
    // resolve our addresses if we haven't yet (couldn't do this in main thread)
    if (_addrs == null) {
      int i = 0;
      _addrs = new InetAddress[_addrsRaw.length];
      _addrsValid = new boolean[_addrsRaw.length];
      for (String a : _addrsRaw) {
        try {
          _addrsValid[i] = false;
          _addrs[i] = InetAddress.getByName(a);
          _addrsValid[i] = true;
        } catch (UnknownHostException e) {
          runOnUiThread(
              () -> {
                String msg = getString(R.string.cantResolveHost);
                Context context = getApplicationContext();
                int duration = Toast.LENGTH_LONG;
                Toast toast = Toast.makeText(context, msg, duration);
                toast.show();
                _lagMeter.setText(msg);
                _lagMeter.setVisibility(View.INVISIBLE);
              });
        }
        i++;
      }
    }
    {
      boolean haveValidAddress = false;
      for (int i = 0; i < _addrs.length; i++) {
        if (!_addrsValid[i]) {
          continue;
        }
        haveValidAddress = true;
        InetAddress addr = _addrs[i];

        DatagramPacket packet = new DatagramPacket(data, len, addr, _port);
        try {
          _socket.send(packet);
        } catch (IOException e) {
          LogThread.log("Error on ID-request send", e, GamePadActivity.this);
          Log.e(TAG, "Error on ID-request send: " + e.getMessage());
          e.printStackTrace();
        } catch (NullPointerException e) {
          LogThread.log("Error on ID-request send", e, GamePadActivity.this);
          Log.e(TAG, "Bad IP specified: " + e.getMessage());
        }
        i++;
      }
      // if no addresses were valid, lets just quit out..
      if (!haveValidAddress) {
        finish();
      }
    }
  }

  private void _readFromSocket(@NonNull DatagramPacket packet) {
    byte[] buffer = packet.getData();
    int amt = packet.getLength();
    if (_processThread != null && Thread.currentThread() != _processThread) {
      Log.v(TAG, "_readFromSocket called in unexpected thread; ignoring.");
      return;
      // throw new AssertionError();
    }
    if (amt > 0) {
      if (buffer[0] == REMOTE_MSG_ID_RESPONSE) {
        if (debug) {
          Log.v(TAG, "Got ID response");
        }
        if (amt == 3) {
          if (_connected) {
            if (debug) {
              Log.v(TAG, "Already connected; ignoring ID response");
            }
            return;
          }
          // for whatever reason .connect started behaving wonky in android 7
          // or so
          // ..(perhaps ipv6 related?..)
          // ..looks like just grabbing the addr and feeding it to our outgoing
          // datagrams works though
          _addr = packet.getAddress();
          // hooray we have an id.. we're now officially connected
          _id = buffer[1];

          // we said we support protocol v2.. if they respond with 100, they
          // do too.
          _usingProtocolV2 = (buffer[2] == 100);
          _nextState = 0; // start over with this ID
          _connected = true;
          _shouldPrintConnected = true;
          if (_id == -1) {
            throw new AssertionError();
          }
        } else {
          Log.e(TAG, "INVALID ID RESPONSE!");
        }
      } else if (buffer[0] == REMOTE_MSG_STATE_ACK) {
        if (amt == 2) {
          long time = SystemClock.uptimeMillis();
          // take note of the next state they want...
          // move ours up to that point (if we haven't yet)
          if (debug) {
            Log.v(TAG, "GOT STATE ACK TO " + (buffer[1] & 0xFF));
          }
          int stateDiff = (buffer[1] - _requestedState) & 0xFF; // upcast to
          // positive
          if (stateDiff > 0 && stateDiff < 128) {
            _requestedState = (_requestedState + (stateDiff - 1)) % 256;
            long lag = time - _stateBirthTimes[_requestedState];
            if (lag > _currentLag) {
              _currentLag = lag;
            }
            _requestedState = (_requestedState + 1) % 256;
            if (_requestedState != (buffer[1] & 0xFF)) {
              throw new AssertionError();
            }
          }
        }
      } else if (buffer[0] == REMOTE_MSG_DISCONNECT_ACK) {
        if (debug) {
          Log.v(TAG, "GOT DISCONNECT ACK!");
        }
        if (amt == 1) {
          _connected = false;
          finish();
        }
      } else if (buffer[0] == REMOTE_MSG_DISCONNECT) {
        if (amt == 2) {
          // ignore disconnect msgs for the first second or two in case we're
          // doing a quick disconnect/reconnect and some old ones come
          // trickling in
          {
            String msg;
            if (buffer[1] == BS_REMOTE_ERROR_VERSION_MISMATCH) {
              msg = getString(R.string.versionMismatch);
            } else if (buffer[1] == BS_REMOTE_ERROR_GAME_SHUTTING_DOWN) {
              msg = getString(R.string.gameShutDown);
            } else if (buffer[1] == BS_REMOTE_ERROR_NOT_ACCEPTING_CONNECTIONS) {
              msg = getString(R.string.gameFull);
            } else {
              msg = getString(R.string.disconnected);
            }
            class ToastRunnable implements Runnable {
              private final String s;

              private ToastRunnable(String sIn) {
                s = sIn;
              }

              public void run() {
                Context context = getApplicationContext();
                int duration = Toast.LENGTH_LONG;
                Toast toast = Toast.makeText(context, s, duration);
                toast.show();
              }
            }
            runOnUiThread(new ToastRunnable(msg));
            _connected = false;
            finish();
          }
        }
      }
    }
  }
}
