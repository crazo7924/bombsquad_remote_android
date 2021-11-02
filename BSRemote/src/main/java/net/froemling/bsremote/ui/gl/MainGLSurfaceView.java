package net.froemling.bsremote.ui.gl;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

import net.froemling.bsremote.LogThread;
import net.froemling.bsremote.R;
import net.froemling.bsremote.ui.GamePadActivity;

import java.util.Set;
import java.util.TreeSet;

public class MainGLSurfaceView extends GLSurfaceView {

  private final GLRenderer gl;
  private final GamePadActivity gamePadActivity;
  private final Set<Integer> mHeldKeys = new TreeSet<>();
  int dPadTouch = -1;
  int prefsTouch = -1;
  int quitTouch = -1;
  int startTouch = -1;
  boolean prefsHover;
  boolean quitHover;
  boolean startHover;
  boolean dPadTouchIsMove;
  boolean initialized = false;
  float buttonCenterX;
  float buttonCenterY;
  float dPadCenterX;
  float dPadCenterY;
  float dPadScale;
  float buttonScale;
  float dPadOffsX;
  float dPadOffsY;
  float buttonOffsX;
  float buttonOffsY;
  float dPadTouchStartX;
  float dPadTouchStartY;
  float sizeMin = 0.5f;
  float sizeMax = 1.5f;
  float dPadOffsXMin = -0.25f;
  float dPadOffsXMax = 1.0f;
  float dPadOffsYMin = -1.0f;
  float dPadOffsYMax = 0.8f;
  float buttonOffsXMin = -0.8f;
  float buttonOffsXMax = 0.25f;
  float buttonOffsYMin = -1.0f;
  float buttonOffsYMax = 0.8f;
  String dPadType;
  private int keyPickUp;
  private int keyJump;
  private int keyPunch;
  private int keyBomb;
  private int keyRun1;
  private int keyRun2;
  private int keyStart;
  private Dialog mPrefsDialog;
  private CaptureKey captureKey = CaptureKey.NONE;
  private boolean mHeldTriggerL = false;
  private boolean mHeldTriggerR = false;
  private float mPhysicalJoystickAxisValueX = 0.0f;
  private float mPhysicalJoystickAxisValueY = 0.0f;
  private float mPhysicalJoystickDPadValueX = 0.0f;
  private float mPhysicalJoystickDPadValueY = 0.0f;
  private float mPhysicalDPadDownVal = 0.0f;
  private float mPhysicalDPadUpVal = 0.0f;
  private float mPhysicalDPadLeftVal = 0.0f;
  private float mPhysicalDPadRightVal = 0.0f;

  public MainGLSurfaceView(Context context) {
    super(context);

    gamePadActivity = (GamePadActivity) context;

    setFocusable(true);
    setFocusableInTouchMode(true);
    requestFocus();

    // Create an OpenGL ES 2.0 context.
    setEGLContextClientVersion(2);

    // Set the Renderer for drawing on the GLSurfaceView
    gl = new GLRenderer(gamePadActivity.getApplicationContext());

    // this seems to be necessary in the emulator..
    if (Build.FINGERPRINT.startsWith("generic")) {
      setEGLConfigChooser(8, 8, 8, 8, 16, 0);
    }
    setRenderer(gl);

    // Render the view only when there is a change in the drawing data
    setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

    SharedPreferences preferences =
        gamePadActivity.getSharedPreferences("BSRemotePrefs", Context.MODE_PRIVATE);
    dPadScale = preferences.getFloat("scale", 1.0f);
    buttonScale = preferences.getFloat("buttonScale", 1.0f);
    dPadScale = preferences.getFloat("dPadScale", 1.0f);
    dPadOffsX = preferences.getFloat("dPadOffsX", 0.4f);
    dPadOffsY = preferences.getFloat("dPadOffsY", 0.0f);
    buttonOffsX = preferences.getFloat("buttonOffsX", -0.2f);
    buttonOffsY = preferences.getFloat("buttonOffsY", 0.0f);
    dPadType = preferences.getString("dPadType", "floating");
    assert dPadType != null;
    if (!(dPadType.equals("floating") || dPadType.equals("fixed"))) {
      dPadType = "floating";
    }

    keyPickUp = preferences.getInt("keyPickUp", KeyEvent.KEYCODE_BUTTON_Y);
    keyJump = preferences.getInt("keyJump", KeyEvent.KEYCODE_BUTTON_A);
    keyPunch = preferences.getInt("keyPunch", KeyEvent.KEYCODE_BUTTON_X);
    keyBomb = preferences.getInt("keyBomb", KeyEvent.KEYCODE_BUTTON_B);
    keyRun1 = preferences.getInt("keyRun1", KeyEvent.KEYCODE_BUTTON_L1);
    keyRun2 = preferences.getInt("keyRun2", KeyEvent.KEYCODE_BUTTON_R1);
    keyStart = preferences.getInt("keyStart", KeyEvent.KEYCODE_BUTTON_START);
  }

  public void onClosing() {
    // if we've got a dialog open, kill it
    if (mPrefsDialog != null && mPrefsDialog.isShowing()) {
      mPrefsDialog.cancel();
    }
  }

  private void savePrefs() {
    // save this to prefs
    SharedPreferences preferences =
        gamePadActivity.getSharedPreferences("BSRemotePrefs", Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = preferences.edit();
    editor.putFloat("buttonScale", buttonScale);
    editor.putFloat("dPadScale", dPadScale);
    editor.putFloat("dPadOffsX", dPadOffsX);
    editor.putFloat("dPadOffsY", dPadOffsY);
    editor.putFloat("buttonOffsX", buttonOffsX);
    editor.putFloat("buttonOffsY", buttonOffsY);
    editor.putString("dPadType", dPadType);

    editor.putInt("keyPickUp", keyPickUp);
    editor.putInt("keyJump", keyJump);
    editor.putInt("keyPunch", keyPunch);
    editor.putInt("keyBomb", keyBomb);
    editor.putInt("keyRun1", keyRun1);
    editor.putInt("keyRun2", keyRun2);
    editor.putInt("keyStart", keyStart);

    editor.apply();
  }

  private void updateSizes() {

    // update button positions and whatnot
    float ratio = (float) getWidth() / getHeight();
    float height = 1.0f / ratio;

    float bWidth = 0.08f * buttonScale;
    float bHeight = 0.08f * buttonScale;
    buttonCenterX = 0.95f - 0.1f * buttonScale + buttonOffsX * 0.2f;
    buttonCenterY = height * 0.6f - 0.0f * buttonScale - buttonOffsY * 0.3f;
    dPadCenterX = 0.0f + 0.1f * dPadScale + dPadOffsX * 0.2f;
    dPadCenterY = height * 0.6f - 0.0f * dPadScale - dPadOffsY * 0.3f;
    float bSep = 0.1f * buttonScale;

    gl.quitButtonX = 0.06f * buttonScale;
    gl.quitButtonY = 0.035f * buttonScale;
    gl.quitButtonWidth = 0.1f * buttonScale;
    gl.quitButtonHeight = 0.05f * buttonScale;

    gl.prefsButtonX = 0.17f * buttonScale;
    gl.prefsButtonY = 0.035f * buttonScale;
    gl.prefsButtonWidth = 0.1f * buttonScale;
    gl.prefsButtonHeight = 0.05f * buttonScale;

    gl.startButtonX = 0.28f * buttonScale;
    gl.startButtonY = 0.035f * buttonScale;
    gl.startButtonWidth = 0.1f * buttonScale;
    gl.startButtonHeight = 0.05f * buttonScale;

    gl.throwButtonX = buttonCenterX;
    gl.throwButtonY = buttonCenterY - bSep;
    gl.throwButtonWidth = bWidth;
    gl.throwButtonHeight = bHeight;

    gl.punchButtonX = buttonCenterX - bSep;
    gl.punchButtonY = buttonCenterY;
    gl.punchButtonWidth = bWidth;
    gl.punchButtonHeight = bHeight;

    gl.bombButtonX = buttonCenterX + bSep;
    gl.bombButtonY = buttonCenterY;
    gl.bombButtonWidth = bWidth;
    gl.bombButtonHeight = bHeight;

    gl.jumpButtonX = buttonCenterX;
    gl.jumpButtonY = buttonCenterY + bSep;
    gl.jumpButtonWidth = bWidth;
    gl.jumpButtonHeight = bHeight;

    gl.joystickCenterX = dPadCenterX;
    gl.joystickCenterY = dPadCenterY;
    gl.joystickWidth = 0.2f * dPadScale;
    gl.joystickHeight = 0.2f * dPadScale;

    gl.joystickX = dPadCenterX;
    gl.joystickY = dPadCenterY;

    if (!initialized) {
      initialized = true;
    }
  }

  public void onSizeChanged(int w, int h, int oldw, int oldh) {
    updateSizes();
  }

  private boolean pointInBox(float x, float y, float bx, float by, float sx, float sy) {
    return x >= bx - 0.5f * sx && x <= bx + 0.5 * sx && y >= by - 0.5 * sy && y <= by + 0.5 * sy;
  }

  void updateButtonsForTouches(MotionEvent event) {

    boolean punchHeld = false;
    boolean throwHeld = false;
    boolean jumpHeld = false;
    boolean bombHeld = false;
    float mult = 1.0f / getWidth();
    final int pointerCount = event.getPointerCount();

    for (int i = 0; i < pointerCount; i++) {

      // ignore touch-up events
      int actionPointerIndex = event.getActionIndex();
      int action = event.getActionMasked();
      if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
          && i == actionPointerIndex) {
        continue;
      }

      int touch = event.getPointerId(i);

      // ignore dpad touch
      if (touch == dPadTouch) {
        continue;
      }

      float s = 4.0f / buttonScale;
      // get the point in button-center-coords
      float x = event.getX(i) * mult;
      float y = event.getY(i) * mult;
      float bx = (x - buttonCenterX) * s;
      float by = (y - buttonCenterY) * s;

      float threshold = 0.3f;
      float pbx, pby;
      float len;
      float punchLen, jumpLen, throwLen, bombLen;

      // punch
      pbx = (x - gl.punchButtonX) * s;
      pby = (y - gl.punchButtonY) * s;
      punchLen = len = (float) Math.sqrt(pbx * pbx + pby * pby);
      if (len < threshold) {
        punchHeld = true;
      }

      // throw
      pbx = (x - gl.throwButtonX) * s;
      pby = (y - gl.throwButtonY) * s;
      throwLen = len = (float) Math.sqrt(pbx * pbx + pby * pby);
      if (len < threshold) {
        throwHeld = true;
      }

      // jump
      pbx = (x - gl.jumpButtonX) * s;
      pby = (y - gl.jumpButtonY) * s;
      jumpLen = len = (float) Math.sqrt(pbx * pbx + pby * pby);
      if (len < threshold) {
        jumpHeld = true;
      }

      // bomb
      pbx = (x - gl.bombButtonX) * s;
      pby = (y - gl.bombButtonY) * s;
      bombLen = len = (float) Math.sqrt(pbx * pbx + pby * pby);
      if (len < threshold) {
        bombHeld = true;
      }

      // how much larger than the button/dpad areas we should count touch
      // events in
      float buttonBuffer = 2.0f;
      // ok now lets take care of fringe areas and non-moved touches
      // a touch in our button area should *always* affect at least one
      // button
      // ..so lets find the closest button && press it.
      // this will probably coincide with what we just set above but thats
      // ok.
      if (x > 0.5
          && bx > -1.0 * buttonBuffer
          && bx < 1.0 * buttonBuffer
          && by > -1.0 * buttonBuffer
          && by < 1.0 * buttonBuffer) {
        if (punchLen < throwLen && punchLen < jumpLen && punchLen < bombLen) {
          punchHeld = true;
        } else if (throwLen < punchLen && throwLen < jumpLen && throwLen < bombLen) {
          throwHeld = true;
        } else if (jumpLen < punchLen && jumpLen < throwLen && jumpLen < bombLen) {
          jumpHeld = true;
        } else {
          bombHeld = true;
        }
      }
    }

    boolean throwWasHeld =
        ((gamePadActivity._buttonStateV1 & GamePadActivity.BS_REMOTE_STATE_THROW) != 0);
    boolean jumpWasHeld =
        ((gamePadActivity._buttonStateV1 & GamePadActivity.BS_REMOTE_STATE_JUMP) != 0);
    boolean punchWasHeld =
        ((gamePadActivity._buttonStateV1 & GamePadActivity.BS_REMOTE_STATE_PUNCH) != 0);
    boolean bombWasHeld =
        ((gamePadActivity._buttonStateV1 & GamePadActivity.BS_REMOTE_STATE_BOMB) != 0);

    // send press events for non-held ones we're now over
    if (!throwWasHeld && throwHeld) {
      handleThrowPress();
    }
    if (throwWasHeld && !throwHeld) {
      handleThrowRelease();
    }

    // send press events for non-held ones we're now over
    if ((!punchWasHeld) && punchHeld) {
      handlePunchPress();
    }
    if (punchWasHeld && !punchHeld) {
      handlePunchRelease();
    }

    // send press events for non-held ones we're now over
    if ((!bombWasHeld) && bombHeld) {
      handleBombPress();
    }
    if (bombWasHeld && !bombHeld) {
      handleBombRelease();
    }

    // send press events for non-held ones we're now over
    if ((!jumpWasHeld) && jumpHeld) {
      handleJumpPress();
    }
    if (jumpWasHeld && !jumpHeld) {
      handleJumpRelease();
    }
  }

  private void handleMenuPress() {
    gamePadActivity._buttonStateV1 |= GamePadActivity.BS_REMOTE_STATE_MENU;
    gamePadActivity._buttonStateV2 |= GamePadActivity.BS_REMOTE_STATE2_MENU;

    gamePadActivity._doStateChange(false);
    gl.startButtonPressed = true;
  }

  private void handleMenuRelease() {
    gamePadActivity._buttonStateV1 &= ~GamePadActivity.BS_REMOTE_STATE_MENU;
    gamePadActivity._buttonStateV2 &= ~GamePadActivity.BS_REMOTE_STATE2_MENU;
    gamePadActivity._doStateChange(false);
    gl.startButtonPressed = false;
  }

  private void handleRunPress() {
    gamePadActivity._buttonStateV2 |= GamePadActivity.BS_REMOTE_STATE2_RUN;
    gamePadActivity._doStateChange(false);
  }

  private void handleRunRelease() {
    gamePadActivity._buttonStateV2 &= ~GamePadActivity.BS_REMOTE_STATE2_RUN;
    gamePadActivity._doStateChange(false);
  }

  private void handleThrowPress() {
    gamePadActivity._buttonStateV1 |= GamePadActivity.BS_REMOTE_STATE_THROW;
    gamePadActivity._buttonStateV2 |= GamePadActivity.BS_REMOTE_STATE2_THROW;
    gamePadActivity._doStateChange(false);
    gl.throwPressed = true;
  }

  private void handleThrowRelease() {
    gamePadActivity._buttonStateV1 &= ~GamePadActivity.BS_REMOTE_STATE_THROW;
    gamePadActivity._buttonStateV2 &= ~GamePadActivity.BS_REMOTE_STATE2_THROW;
    gamePadActivity._doStateChange(false);
    gl.throwPressed = false;
  }

  private void handleBombPress() {
    gamePadActivity._buttonStateV1 |= GamePadActivity.BS_REMOTE_STATE_BOMB;
    gamePadActivity._buttonStateV2 |= GamePadActivity.BS_REMOTE_STATE2_BOMB;
    gamePadActivity._doStateChange(false);
    gl.bombPressed = true;
  }

  private void handleBombRelease() {
    gamePadActivity._buttonStateV1 &= ~GamePadActivity.BS_REMOTE_STATE_BOMB;
    gamePadActivity._buttonStateV2 &= ~GamePadActivity.BS_REMOTE_STATE2_BOMB;
    gamePadActivity._doStateChange(false);
    gl.bombPressed = false;
  }

  private void handleJumpPress() {
    gamePadActivity._buttonStateV1 |= GamePadActivity.BS_REMOTE_STATE_JUMP;
    gamePadActivity._buttonStateV2 |= GamePadActivity.BS_REMOTE_STATE2_JUMP;
    gamePadActivity._doStateChange(false);
    gl.jumpPressed = true;
  }

  private void handleJumpRelease() {
    gamePadActivity._buttonStateV1 &= ~GamePadActivity.BS_REMOTE_STATE_JUMP;
    gamePadActivity._buttonStateV2 &= ~GamePadActivity.BS_REMOTE_STATE2_JUMP;
    gamePadActivity._doStateChange(false);
    gl.jumpPressed = false;
  }

  private void handlePunchPress() {
    gamePadActivity._buttonStateV1 |= GamePadActivity.BS_REMOTE_STATE_PUNCH;
    gamePadActivity._buttonStateV2 |= GamePadActivity.BS_REMOTE_STATE2_PUNCH;
    gamePadActivity._doStateChange(false);
    gl.punchPressed = true;
  }

  private void handlePunchRelease() {
    gamePadActivity._buttonStateV1 &= ~GamePadActivity.BS_REMOTE_STATE_PUNCH;
    gamePadActivity._buttonStateV2 &= ~GamePadActivity.BS_REMOTE_STATE2_PUNCH;
    gamePadActivity._doStateChange(false);
    gl.punchPressed = false;
  }

  public void doPrefs() {

    final Dialog d = new Dialog(gamePadActivity);
    mPrefsDialog = d;

    d.setContentView(R.layout.prefs);
    d.setCanceledOnTouchOutside(true);

    SeekBar seekbar;
    seekbar = d.findViewById(R.id.seekBarButtonSize);
    seekbar.setProgress((int) (100.0f * (buttonScale - sizeMin) / (sizeMax - sizeMin)));
    seekbar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            buttonScale = sizeMin + (sizeMax - sizeMin) * (progress / 100.0f);
            updateSizes();
            requestRender();
          }

          public void onStartTrackingTouch(SeekBar seekBar) {}

          public void onStopTrackingTouch(SeekBar seekBar) {
            savePrefs();
          }
        });
    seekbar = d.findViewById(R.id.seekBarDPadSize);
    seekbar.setProgress((int) (100.0f * (dPadScale - sizeMin) / (sizeMax - sizeMin)));
    seekbar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            dPadScale = sizeMin + (sizeMax - sizeMin) * (progress / 100.0f);
            updateSizes();
            requestRender();
          }

          public void onStartTrackingTouch(SeekBar seekBar) {}

          public void onStopTrackingTouch(SeekBar seekBar) {
            savePrefs();
          }
        });
    seekbar = d.findViewById(R.id.seekBarButtonPosition1);
    seekbar.setProgress(
        (int) (100.0f * (buttonOffsX - buttonOffsXMin) / (buttonOffsXMax - buttonOffsXMin)));
    seekbar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            buttonOffsX = buttonOffsXMin + (buttonOffsXMax - buttonOffsXMin) * (progress / 100.0f);
            updateSizes();
            requestRender();
          }

          public void onStartTrackingTouch(SeekBar seekBar) {}

          public void onStopTrackingTouch(SeekBar seekBar) {
            savePrefs();
          }
        });
    seekbar = d.findViewById(R.id.seekBarButtonPosition2);
    seekbar.setProgress(
        (int) (100.0f * (buttonOffsY - buttonOffsYMin) / (buttonOffsYMax - buttonOffsYMin)));
    seekbar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            buttonOffsY = buttonOffsYMin + (buttonOffsYMax - buttonOffsYMin) * (progress / 100.0f);
            updateSizes();
            requestRender();
          }

          public void onStartTrackingTouch(SeekBar seekBar) {}

          public void onStopTrackingTouch(SeekBar seekBar) {
            savePrefs();
          }
        });
    seekbar = d.findViewById(R.id.seekBarDPadPosition1);
    seekbar.setProgress(
        (int) (100.0f * (dPadOffsX - dPadOffsXMin) / (dPadOffsXMax - dPadOffsXMin)));
    seekbar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            dPadOffsX = dPadOffsXMin + (dPadOffsXMax - dPadOffsXMin) * (progress / 100.0f);
            updateSizes();
            requestRender();
          }

          public void onStartTrackingTouch(SeekBar seekBar) {}

          public void onStopTrackingTouch(SeekBar seekBar) {
            savePrefs();
          }
        });
    seekbar = d.findViewById(R.id.seekBarDPadPosition2);
    seekbar.setProgress(
        (int) (100.0f * (dPadOffsY - dPadOffsYMin) / (dPadOffsYMax - dPadOffsYMin)));
    seekbar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            dPadOffsY = dPadOffsYMin + (dPadOffsYMax - dPadOffsYMin) * (progress / 100.0f);
            updateSizes();
            requestRender();
          }

          public void onStartTrackingTouch(SeekBar seekBar) {}

          public void onStopTrackingTouch(SeekBar seekBar) {
            savePrefs();
          }
        });

    RadioButton dPadFloatingButton = d.findViewById(R.id.radioButtonDPadFloating);
    RadioButton dPadFixedButton = d.findViewById(R.id.radioButtonDPadFixed);

    if (dPadType.equals("floating")) {
      dPadFloatingButton.setChecked(true);
    } else {
      dPadFixedButton.setChecked(true);
    }

    dPadFloatingButton.setOnClickListener(
        v -> {
          dPadType = "floating";
          savePrefs();
        });
    dPadFixedButton.setOnClickListener(
        v -> {
          dPadType = "fixed";
          savePrefs();
        });

    Button configHardwareButton = d.findViewById(R.id.buttonConfigureHardwareButtons);
    configHardwareButton.setOnClickListener(
        v -> {
          // kill this dialog and bring up the hardware one
          d.cancel();
          doHardwareControlsPrefs();
        });

    d.setTitle(R.string.settings);
    d.show();
  }

  public String getPrettyKeyName(int keyCode) {
    String val = KeyEvent.keyCodeToString(keyCode);
    if (val.startsWith("KEYCODE_")) {
      val = val.replaceAll("KEYCODE_", "");
    }
    if (val.startsWith("BUTTON_")) {
      val = val.replaceAll("BUTTON_", "");
    }
    return val;
  }

  public Dialog doCaptureKey() {

    final Dialog d = new Dialog(gamePadActivity);
    d.setContentView(R.layout.prefs_capture_key);
    d.setCanceledOnTouchOutside(true);
    d.setOnKeyListener(
        (arg0, keyCode, event) -> {
          setActionKey(keyCode);
          d.dismiss();
          return true;
        });

    Button b = d.findViewById(R.id.buttonResetToDefault);
    b.setOnClickListener(
        v -> {
          switch (captureKey) {
            case PICK_UP:
              setActionKey(KeyEvent.KEYCODE_BUTTON_Y);
              break;
            case JUMP:
              setActionKey(KeyEvent.KEYCODE_BUTTON_A);
              break;
            case PUNCH:
              setActionKey(KeyEvent.KEYCODE_BUTTON_X);
              break;
            case BOMB:
              setActionKey(KeyEvent.KEYCODE_BUTTON_B);
              break;
            case RUN1:
              setActionKey(KeyEvent.KEYCODE_BUTTON_L1);
              break;
            case RUN2:
              setActionKey(KeyEvent.KEYCODE_BUTTON_R1);
              break;
            case START:
              setActionKey(KeyEvent.KEYCODE_BUTTON_START);
              break;
            default:
              LogThread.log("Error: unrecognized key in doActionKey", null, d.getContext());
              break;
          }
          d.dismiss();
        });

    d.setTitle(R.string.capturing);
    d.show();
    return d;
  }

  private void setActionKey(int keyval) {
    switch (captureKey) {
      case PICK_UP:
        keyPickUp = keyval;
        break;
      case JUMP:
        keyJump = keyval;
        break;
      case PUNCH:
        keyPunch = keyval;
        break;
      case BOMB:
        keyBomb = keyval;
        break;
      case RUN1:
        keyRun1 = keyval;
        break;
      case RUN2:
        keyRun2 = keyval;
        break;
      case START:
        keyStart = keyval;
        break;
      default:
        LogThread.log("Error: unrecognized key in setActionKey", null, getContext());
        break;
    }
    savePrefs();
  }

  private void updateHardwareControlsLabels(Dialog d) {
    TextView t = d.findViewById(R.id.textPickUp);
    t.setText(getPrettyKeyName(keyPickUp));
    t = d.findViewById(R.id.textJump);
    t.setText(getPrettyKeyName(keyJump));
    t = d.findViewById(R.id.textPunch);
    t.setText(getPrettyKeyName(keyPunch));
    t = d.findViewById(R.id.textBomb);
    t.setText(getPrettyKeyName(keyBomb));
    t = d.findViewById(R.id.textRun1);
    t.setText(getPrettyKeyName(keyRun1));
    t = d.findViewById(R.id.textRun2);
    t.setText(getPrettyKeyName(keyRun2));
    t = d.findViewById(R.id.textStart);
    t.setText(getPrettyKeyName(keyStart));
  }

  public void doHardwareControlsPrefs() {

    final Dialog d = new Dialog(gamePadActivity);
    d.setContentView(R.layout.prefs_hardware_controls);
    d.setCanceledOnTouchOutside(true);
    OnClickListener l =
        v -> {

          // take note of which action this capture will apply to, then launch
          // a capture..
          if (v == d.findViewById(R.id.buttonPickUp)) {
            captureKey = CaptureKey.PICK_UP;
          } else if (v == d.findViewById(R.id.buttonJump)) {
            captureKey = CaptureKey.JUMP;
          } else if (v == d.findViewById(R.id.buttonPunch)) {
            captureKey = CaptureKey.PUNCH;
          } else if (v == d.findViewById(R.id.buttonBomb)) {
            captureKey = CaptureKey.BOMB;
          } else if (v == d.findViewById(R.id.buttonRun1)) {
            captureKey = CaptureKey.RUN1;
          } else if (v == d.findViewById(R.id.buttonRun2)) {
            captureKey = CaptureKey.RUN2;
          } else if (v == d.findViewById(R.id.buttonStart)) {
            captureKey = CaptureKey.START;
          } else {
            LogThread.log("Error: unrecognized capture button", null, getContext());
          }

          Dialog d2 = doCaptureKey();
          d2.setOnDismissListener(dialog -> updateHardwareControlsLabels(d));
        };

    d.findViewById(R.id.buttonPickUp).setOnClickListener(l);
    d.findViewById(R.id.buttonJump).setOnClickListener(l);
    d.findViewById(R.id.buttonPunch).setOnClickListener(l);
    d.findViewById(R.id.buttonBomb).setOnClickListener(l);
    d.findViewById(R.id.buttonRun1).setOnClickListener(l);
    d.findViewById(R.id.buttonRun2).setOnClickListener(l);
    d.findViewById(R.id.buttonStart).setOnClickListener(l);

    d.setTitle(R.string.configHardwareButtons);
    updateHardwareControlsLabels(d);
    d.show();
  }

  // Generic-motion events
  @Override
  public boolean onGenericMotionEvent(MotionEvent event) {

    boolean handled = false;
    boolean changed = false;

    if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {

      final int historySize = event.getHistorySize();
      // Append all historical values in the batch.
      for (int historyPos = 0; historyPos < (historySize + 1); historyPos++) {

        float valueAxisX;
        float valueAxisY;
        float valueDPadX;
        float valueDPadY;
        float valueTriggerL;
        float valueTriggerR;

        // go through historical values and current
        if (historyPos < historySize) {
          valueAxisX = event.getHistoricalAxisValue(MotionEvent.AXIS_X, historyPos);
          valueAxisY = event.getHistoricalAxisValue(MotionEvent.AXIS_Y, historyPos);
          valueDPadX = event.getHistoricalAxisValue(MotionEvent.AXIS_HAT_X, historyPos);
          valueDPadY = event.getHistoricalAxisValue(MotionEvent.AXIS_HAT_Y, historyPos);
          valueTriggerL = event.getHistoricalAxisValue(MotionEvent.AXIS_LTRIGGER, historyPos);
          valueTriggerR = event.getHistoricalAxisValue(MotionEvent.AXIS_RTRIGGER, historyPos);

        } else {
          valueAxisX = event.getAxisValue(MotionEvent.AXIS_X);
          valueAxisY = event.getAxisValue(MotionEvent.AXIS_Y);
          valueDPadX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
          valueDPadY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
          valueTriggerL = event.getAxisValue(MotionEvent.AXIS_LTRIGGER);
          valueTriggerR = event.getAxisValue(MotionEvent.AXIS_RTRIGGER);
        }
        boolean triggerHeldL = (valueTriggerL >= 0.5);
        boolean triggerHeldR = (valueTriggerR >= 0.5);

        // handle trigger state changes
        if (triggerHeldL != mHeldTriggerL || triggerHeldR != mHeldTriggerR) {
          boolean runWasHeld = ((!this.mHeldKeys.isEmpty()) || mHeldTriggerL || mHeldTriggerR);
          mHeldTriggerL = triggerHeldL;
          mHeldTriggerR = triggerHeldR;
          boolean runIsHeld = ((!this.mHeldKeys.isEmpty()) || mHeldTriggerL || mHeldTriggerR);
          if (!runWasHeld) {
            // if (!runWasHeld && runIsHeld) {
            // (logically runIsHeld is always true here; shutting up lint)
            handleRunPress();
          }
          if (runWasHeld && !runIsHeld) {
            handleRunRelease();
          }
          changed = true;
        }

        // handle dpad state changes
        if (dPadTouch == -1
            && (valueAxisX != mPhysicalJoystickAxisValueX
                || valueAxisY != mPhysicalJoystickAxisValueY
                || valueDPadX != mPhysicalJoystickDPadValueX
                || valueDPadY != mPhysicalJoystickDPadValueY)) {
          float valueX;
          float valueY;
          boolean valid;
          // if our dpad has changed, use that value..
          if (valueDPadX != mPhysicalJoystickDPadValueX
              || valueDPadY != mPhysicalJoystickDPadValueY) {
            valueX = valueDPadX;
            valueY = valueDPadY;
            valid = true;
          } else {
            // otherwise, use the normal axis value *unless* we've got a dpad
            // press going on
            // (wanna avoid having analog axis noise wipe out dpad presses)
            valueX = valueAxisX;
            valueY = valueAxisY;
            valid =
                (Math.abs(mPhysicalJoystickDPadValueX) < 0.1
                    && Math.abs(mPhysicalJoystickDPadValueY) < 0.1);
          }
          mPhysicalJoystickAxisValueX = valueAxisX;
          mPhysicalJoystickAxisValueY = valueAxisY;
          mPhysicalJoystickDPadValueX = valueDPadX;
          mPhysicalJoystickDPadValueY = valueDPadY;

          if (valid) {
            float s = 30.0f / dPadScale;
            if (valueX < -1.0f) {
              valueX = -1.0f;
            } else if (valueX > 1.0f) {
              valueX = 1.0f;
            }
            if (valueY < -1.0f) {
              valueY = -1.0f;
            } else if (valueY > 1.0f) {
              valueY = 1.0f;
            }

            gl.joystickX = gl.joystickCenterX + valueX / s;
            gl.joystickY = gl.joystickCenterY + valueY / s;

            gamePadActivity._dPadStateH = valueX;
            gamePadActivity._dPadStateV = valueY;
            gamePadActivity._doStateChange(false);

            changed = true;
          }
        }
        handled = true;
      }
    }
    if (changed) {
      requestRender();
    }
    return handled;
  }

  private void handlePhysicalDPadEvent() {
    float valueX = mPhysicalDPadRightVal - mPhysicalDPadLeftVal;
    float valueY = mPhysicalDPadDownVal - mPhysicalDPadUpVal;

    float s = 30.0f / dPadScale;
    if (valueX < -1.0f) {
      valueX = -1.0f;
    } else if (valueX > 1.0f) {
      valueX = 1.0f;
    }
    if (valueY < -1.0f) {
      valueY = -1.0f;
    } else if (valueY > 1.0f) {
      valueY = 1.0f;
    }

    gl.joystickX = gl.joystickCenterX + valueX / s;
    gl.joystickY = gl.joystickCenterY + valueY / s;

    gamePadActivity._dPadStateH = valueX;
    gamePadActivity._dPadStateV = valueY;
    gamePadActivity._doStateChange(false);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    boolean throwWasHeld =
        ((gamePadActivity._buttonStateV1 & GamePadActivity.BS_REMOTE_STATE_THROW) != 0);
    boolean jumpWasHeld =
        ((gamePadActivity._buttonStateV1 & GamePadActivity.BS_REMOTE_STATE_JUMP) != 0);
    boolean punchWasHeld =
        ((gamePadActivity._buttonStateV1 & GamePadActivity.BS_REMOTE_STATE_PUNCH) != 0);
    boolean bombWasHeld =
        ((gamePadActivity._buttonStateV1 & GamePadActivity.BS_REMOTE_STATE_BOMB) != 0);
    boolean menuWasHeld =
        ((gamePadActivity._buttonStateV1 & GamePadActivity.BS_REMOTE_STATE_MENU) != 0);
    boolean handled = false;

    // check for custom assigned keys:
    if (keyCode == keyPickUp) {
      if (!throwWasHeld) {
        handleThrowPress();
      }
      handled = true;
    } else if (keyCode == keyJump) {
      if (!jumpWasHeld) {
        handleJumpPress();
      }
      handled = true;
    } else if (keyCode == keyPunch) {
      if (!punchWasHeld) {
        handlePunchPress();
      }
      handled = true;
    } else if (keyCode == keyBomb) {
      if (!bombWasHeld) {
        handleBombPress();
      }
      handled = true;
    } else if (keyCode == keyStart) {
      if (!menuWasHeld) {
        handleMenuPress();
      }
      handled = true;
    } else if ((keyCode == keyRun1) || (keyCode == keyRun2)) {
      boolean runWasHeld = ((!this.mHeldKeys.isEmpty()) || mHeldTriggerL || mHeldTriggerR);
      Integer kcInt = keyCode;
      this.mHeldKeys.add(kcInt);
      boolean runIsHeld = ((!this.mHeldKeys.isEmpty()) || mHeldTriggerL || mHeldTriggerR);
      if (!runWasHeld && runIsHeld) {
        handleRunPress();
      }
      handled = true;
    } else {
      // resort to hard-coded defaults..
      switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_UP:
          mPhysicalDPadUpVal = 1.0f;
          handlePhysicalDPadEvent();
          handled = true;
          break;
        case KeyEvent.KEYCODE_DPAD_DOWN:
          mPhysicalDPadDownVal = 1.0f;
          handlePhysicalDPadEvent();
          handled = true;
          break;
        case KeyEvent.KEYCODE_DPAD_LEFT:
          mPhysicalDPadLeftVal = 1.0f;
          handlePhysicalDPadEvent();
          handled = true;
          break;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
          mPhysicalDPadRightVal = 1.0f;
          handlePhysicalDPadEvent();
          handled = true;
          break;

        case KeyEvent.KEYCODE_BUTTON_A:
          if (!jumpWasHeld) {
            handleJumpPress();
          }
          handled = true;
          break;
        case KeyEvent.KEYCODE_BUTTON_B:
          if (!bombWasHeld) {
            handleBombPress();
          }
          handled = true;
          break;
        case KeyEvent.KEYCODE_BUTTON_X:
          if (!punchWasHeld) {
            handlePunchPress();
          }
          handled = true;
          break;
        case KeyEvent.KEYCODE_BUTTON_Y:
          if (!throwWasHeld) {
            handleThrowPress();
          }
          handled = true;
          break;
        case KeyEvent.KEYCODE_MENU:
        case KeyEvent.KEYCODE_BUTTON_START:
          if (!menuWasHeld) {
            handleMenuPress();
          }
          handled = true;
          break;
        default:
          if (isRunKey(keyCode)) {
            boolean runWasHeld = ((!this.mHeldKeys.isEmpty()) || mHeldTriggerL || mHeldTriggerR);
            Integer kcInt = keyCode;
            this.mHeldKeys.add(kcInt);
            boolean runIsHeld = ((!this.mHeldKeys.isEmpty()) || mHeldTriggerL || mHeldTriggerR);
            if (!runWasHeld && runIsHeld) {
              handleRunPress();
            }
            handled = true;
          }
          break;
      }
    }
    if (handled) {
      requestRender();
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  boolean isRunKey(int keyCode) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_BUTTON_R1:
      case KeyEvent.KEYCODE_BUTTON_R2:
      case KeyEvent.KEYCODE_BUTTON_L1:
      case KeyEvent.KEYCODE_BUTTON_L2:
      case KeyEvent.KEYCODE_VOLUME_UP:
      case KeyEvent.KEYCODE_VOLUME_DOWN:
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {

    boolean throwWasHeld =
        ((gamePadActivity._buttonStateV1 & GamePadActivity.BS_REMOTE_STATE_THROW) != 0);
    boolean jumpWasHeld =
        ((gamePadActivity._buttonStateV1 & GamePadActivity.BS_REMOTE_STATE_JUMP) != 0);
    boolean punchWasHeld =
        ((gamePadActivity._buttonStateV1 & GamePadActivity.BS_REMOTE_STATE_PUNCH) != 0);
    boolean bombWasHeld =
        ((gamePadActivity._buttonStateV1 & GamePadActivity.BS_REMOTE_STATE_BOMB) != 0);
    boolean menuWasHeld =
        ((gamePadActivity._buttonStateV1 & GamePadActivity.BS_REMOTE_STATE_MENU) != 0);

    boolean handled = false;

    // handle our custom-assigned keys
    if (keyCode == keyPickUp) {
      if (throwWasHeld) {
        handleThrowRelease();
      }
      handled = true;
    } else if (keyCode == keyJump) {
      if (jumpWasHeld) {
        handleJumpRelease();
      }
      handled = true;
    } else if (keyCode == keyPunch) {
      if (punchWasHeld) {
        handlePunchRelease();
      }
      handled = true;
    } else if (keyCode == keyBomb) {
      if (bombWasHeld) {
        handleBombRelease();
      }
      handled = true;
    } else if (keyCode == keyStart) {
      if (menuWasHeld) {
        handleMenuRelease();
      }
      handled = true;
    } else if ((keyCode == keyRun1) || (keyCode == keyRun2)) {
      boolean runWasHeld = ((!this.mHeldKeys.isEmpty()) || mHeldTriggerL || mHeldTriggerR);
      Integer kcInt = keyCode;
      this.mHeldKeys.remove(kcInt);
      boolean runIsHeld = ((!this.mHeldKeys.isEmpty()) || mHeldTriggerL || mHeldTriggerR);
      if (runWasHeld && !runIsHeld) {
        handleRunRelease();
      }
      handled = true;
    } else {
      // fall back on hard-coded defaults
      switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_UP:
          mPhysicalDPadUpVal = 0.0f;
          handlePhysicalDPadEvent();
          handled = true;
          break;
        case KeyEvent.KEYCODE_DPAD_DOWN:
          mPhysicalDPadDownVal = 0.0f;
          handlePhysicalDPadEvent();
          handled = true;
          break;
        case KeyEvent.KEYCODE_DPAD_LEFT:
          mPhysicalDPadLeftVal = 0.0f;
          handlePhysicalDPadEvent();
          handled = true;
          break;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
          mPhysicalDPadRightVal = 0.0f;
          handlePhysicalDPadEvent();
          handled = true;
          break;

        case KeyEvent.KEYCODE_BUTTON_A:
          if (jumpWasHeld) {
            handleJumpRelease();
          }
          handled = true;
          break;
        case KeyEvent.KEYCODE_BUTTON_B:
          if (bombWasHeld) {
            handleBombRelease();
          }
          handled = true;
          break;
        case KeyEvent.KEYCODE_BUTTON_X:
          if (punchWasHeld) {
            handlePunchRelease();
          }
          handled = true;
          break;
        case KeyEvent.KEYCODE_BUTTON_Y:
          if (throwWasHeld) {
            handleThrowRelease();
          }
          handled = true;
          break;
        case KeyEvent.KEYCODE_MENU:
        case KeyEvent.KEYCODE_BUTTON_START:
          if (menuWasHeld) {
            handleMenuRelease();
          }
          handled = true;
          break;
        default:
          if (isRunKey(keyCode)) {
            boolean runWasHeld = ((!this.mHeldKeys.isEmpty()) || mHeldTriggerL || mHeldTriggerR);
            Integer kcInt = keyCode;
            this.mHeldKeys.remove(kcInt);
            boolean runIsHeld = ((!this.mHeldKeys.isEmpty()) || mHeldTriggerL || mHeldTriggerR);
            if (runWasHeld && !runIsHeld) {
              handleRunRelease();
            }
            handled = true;
          }
          break;
      }
    }

    if (handled) {
      requestRender();
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }

  // Touch events
  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    final int pointerCount = event.getPointerCount();
    int actionPointerIndex = event.getActionIndex();
    int action = event.getActionMasked();

    float mult = 1.0f / getWidth();

    // touch margin
    float tm = 1.4f;
    float tm2 = 2.4f;

    for (int i = 0; i < pointerCount; i++) {
      float x = event.getX(i) * mult;
      float y = event.getY(i) * mult;
      int fingerID = event.getPointerId(i);

      if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)
          && (actionPointerIndex == i)) {

        if (pointInBox(
            x,
            y,
            gl.prefsButtonX,
            gl.prefsButtonY,
            gl.prefsButtonWidth * tm,
            gl.prefsButtonHeight * tm2)) {
          // prefs presses
          prefsTouch = fingerID;
          prefsHover = true;
          gl.prefsButtonPressed = true;
        } else if (pointInBox(
            x,
            y,
            gl.quitButtonX,
            gl.quitButtonY,
            gl.quitButtonWidth * tm,
            gl.quitButtonHeight * tm2)) {
          // check for a quit press
          quitTouch = fingerID;
          quitHover = true;
          gl.quitButtonPressed = true;
        } else if (pointInBox(
            x,
            y,
            gl.startButtonX,
            gl.startButtonY,
            gl.startButtonWidth * tm,
            gl.startButtonHeight * tm2)) {
          // check for a start press
          startTouch = fingerID;
          startHover = true;
          gl.startButtonPressed = true;
        } else if (x < 0.5) {
          // check for a dpad touch
          dPadTouch = fingerID;
          // in fixed joystick mode we want touches to count towards
          // joystick motion immediately; not just after they move
          dPadTouchIsMove = dPadType.equals("fixed");
          dPadTouchStartX = x;
          dPadTouchStartY = y;
          gl.joystickX = x;
          gl.joystickY = y;
          gl.thumbPressed = true;
        }
      }
      // handle existing button touches
      if (fingerID == quitTouch) {
        // update position
        quitHover =
            gl.quitButtonPressed =
                (pointInBox(
                    x,
                    y,
                    gl.quitButtonX,
                    gl.quitButtonY,
                    gl.quitButtonWidth * tm,
                    gl.quitButtonHeight * tm2));
        // if the touch is ending...
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
            && (actionPointerIndex == i)) {
          quitTouch = -1;
          // _quitHover = false;
          gl.quitButtonPressed = false;
          if (quitHover) {

            // ewwww - seeing that in some cases our onDestroy()
            // doesn't get called for a while which keeps the server
            // from announcing our departure.  lets just shoot off
            // one random disconnect packet here to hopefully speed that along.
            gamePadActivity.sendDisconnectPacket();
            gamePadActivity.finish();
          }
        }
      } else if (fingerID == prefsTouch) {
        // update position
        prefsHover =
            gl.prefsButtonPressed =
                (pointInBox(
                    x,
                    y,
                    gl.prefsButtonX,
                    gl.prefsButtonY,
                    gl.prefsButtonWidth * tm,
                    gl.prefsButtonHeight * tm2));
        // if the touch is ending...
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
            && (actionPointerIndex == i)) {
          prefsTouch = -1;
          gl.prefsButtonPressed = false;
          if (prefsHover) {
            doPrefs();
          }
        }
      } else if (fingerID == startTouch) {
        // update position
        startHover =
            gl.startButtonPressed =
                (pointInBox(
                    x,
                    y,
                    gl.startButtonX,
                    gl.startButtonY,
                    gl.startButtonWidth * tm,
                    gl.startButtonHeight * tm2));
        // if the touch is ending...
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
            && (actionPointerIndex == i)) {
          startTouch = -1;
          gl.startButtonPressed = false;
          if (startHover) {
            // send 2 state-changes (start-down and start-up)
            gamePadActivity._buttonStateV1 |= GamePadActivity.BS_REMOTE_STATE_MENU;
            gamePadActivity._buttonStateV2 |= GamePadActivity.BS_REMOTE_STATE2_MENU;
            gamePadActivity._doStateChange(false);
            gamePadActivity._buttonStateV1 &= ~GamePadActivity.BS_REMOTE_STATE_MENU;
            gamePadActivity._buttonStateV2 &= ~GamePadActivity.BS_REMOTE_STATE2_MENU;
            gamePadActivity._doStateChange(false);
          }
        }
      }

      // if its our existing dpad-touch
      if (fingerID == dPadTouch) {

        // if we've moved away from the initial touch position we no longer
        // consider it for a direction tap
        if (!dPadTouchIsMove
            && (Math.abs(x - dPadTouchStartX) > 0.01 || Math.abs(y - dPadTouchStartY) > 0.01)) {
          dPadTouchIsMove = true;
          gl.joystickCenterX = dPadTouchStartX;
          gl.joystickCenterY = dPadTouchStartY;
        }
        // if its moved, pass it along as a joystick event
        if (dPadTouchIsMove) {
          float s = 30.0f / dPadScale;
          float xVal = (x - gl.joystickCenterX) * s;
          float yVal = (y - gl.joystickCenterY) * s;
          float xValClamped = xVal;
          float yValClamped = yVal;

          // keep our H/V values within a unit box
          // (originally I clamped length to 1 but as a result our diagonal
          // running speed was less than other analog controllers..)

          if (xValClamped > 1.0f) {
            float m = 1.0f / xValClamped;
            xValClamped *= m;
            yValClamped *= m;
          } else if (xValClamped < -1.0f) {
            float m = -1.0f / xValClamped;
            xValClamped *= m;
            yValClamped *= m;
          }
          if (yValClamped > 1.0f) {
            float m = 1.0f / yValClamped;
            xValClamped *= m;
            yValClamped *= m;
          } else if (yValClamped < -1.0f) {
            float m = -1.0f / yValClamped;
            xValClamped *= m;
            yValClamped *= m;
          }

          gl.joystickX = gl.joystickCenterX + xVal / s;
          gl.joystickY = gl.joystickCenterY + yVal / s;

          // if its moved far enough away from center, have the dpad
          // follow it (in floating mode)
          // in fixed mode just clamp distance
          float dist = (float) Math.sqrt(xVal * xVal + yVal * yVal);
          if (dPadType.equals("floating")) {
            if (dist > 1.5f) {
              float sc = 1.5f / dist;
              gl.joystickCenterX = gl.joystickX - sc * (xVal / s);
              gl.joystickCenterY = gl.joystickY - sc * (yVal / s);
            }
          } else if (dPadType.equals("fixed")) {
            if (dist > 1.01f) {
              float sc = 1.01f / dist;
              gl.joystickX = gl.joystickCenterX + sc * (xVal / s);
              gl.joystickY = gl.joystickCenterY + sc * (yVal / s);
            }
          }

          gamePadActivity._dPadStateH = xValClamped;
          gamePadActivity._dPadStateV = yValClamped;
          gamePadActivity._doStateChange(false);
        }

        // if the touch is ending...
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
            && (actionPointerIndex == i)) {

          // if we hadn't moved yet, lets pass it along as a quick tap/release
          // (useful for navigating menus)
          if (!dPadTouchIsMove) {
            float toRight = x - gl.joystickCenterX;
            float toLeft = gl.joystickCenterX - x;
            float toBottom = y - gl.joystickCenterY;
            float toTop = gl.joystickCenterY - y;
            // right
            if (toRight > toLeft && toRight > toTop && toRight > toBottom) {
              gamePadActivity._dPadStateH = 1.0f;
              gamePadActivity._dPadStateV = 0.0f;
              gamePadActivity._doStateChange(false);
              gamePadActivity._dPadStateH = 0.0f;
              gamePadActivity._dPadStateV = 0.0f;
              gamePadActivity._doStateChange(false);
            } else if (toLeft > toRight && toLeft > toTop && toLeft > toBottom) {
              // left
              gamePadActivity._dPadStateH = -1.0f;
              gamePadActivity._dPadStateV = 0.0f;
              gamePadActivity._doStateChange(false);
              gamePadActivity._dPadStateH = 0.0f;
              gamePadActivity._dPadStateV = 0.0f;
              gamePadActivity._doStateChange(false);
            } else if (toTop > toRight && toTop > toLeft && toTop > toBottom) {
              gamePadActivity._dPadStateH = 0.0f;
              gamePadActivity._dPadStateV = -1.0f;
              gamePadActivity._doStateChange(false);
              gamePadActivity._dPadStateH = 0.0f;
              gamePadActivity._dPadStateV = 0.0f;
              gamePadActivity._doStateChange(false);

            } else {
              gamePadActivity._dPadStateH = 0.0f;
              gamePadActivity._dPadStateV = 1.0f;
              gamePadActivity._doStateChange(false);
              gamePadActivity._dPadStateH = 0.0f;
              gamePadActivity._dPadStateV = 0.0f;
              gamePadActivity._doStateChange(false);
            }
          }
          dPadTouch = -1;
          gl.thumbPressed = false;
          gl.joystickCenterX = dPadCenterX;
          gl.joystickCenterY = dPadCenterY;
          gl.joystickX = gl.joystickCenterX;
          gl.joystickY = gl.joystickCenterY;
          gamePadActivity._dPadStateH = 0;
          gamePadActivity._dPadStateV = 0;
          gamePadActivity._doStateChange(false);
        }
      }
    }
    updateButtonsForTouches(event);
    requestRender();
    return true;
  }
}
