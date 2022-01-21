package net.froemling.bsremote.ui.gl

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import net.froemling.bsremote.LogThread.Companion.log
import net.froemling.bsremote.R
import net.froemling.bsremote.ui.GamePadActivity
import java.util.*

class MainGLSurfaceView(context: Context) : GLSurfaceView(context) {
    private val gl: GLRenderer
    private val gamePadActivity: GamePadActivity = context as GamePadActivity
    private val mHeldKeys: MutableSet<Int> = TreeSet()
    var dPadTouch = -1
    var prefsTouch = -1
    var quitTouch = -1
    var startTouch = -1
    var prefsHover = false
    var quitHover = false
    var startHover = false
    var dPadTouchIsMove = false
    var initialized = false
    var buttonCenterX = 0f
    var buttonCenterY = 0f
    var dPadCenterX = 0f
    var dPadCenterY = 0f
    var dPadScale: Float
    var buttonScale: Float
    var dPadOffsX: Float
    var dPadOffsY: Float
    var buttonOffsX: Float
    var buttonOffsY: Float
    var dPadTouchStartX = 0f
    var dPadTouchStartY = 0f
    var sizeMin = 0.5f
    var sizeMax = 1.5f
    var dPadOffsXMin = -0.25f
    var dPadOffsXMax = 1.0f
    var dPadOffsYMin = -1.0f
    var dPadOffsYMax = 0.8f
    var buttonOffsXMin = -0.8f
    var buttonOffsXMax = 0.25f
    var buttonOffsYMin = -1.0f
    var buttonOffsYMax = 0.8f
    var dPadType: String?
    private var keyPickUp: Int
    private var keyJump: Int
    private var keyPunch: Int
    private var keyBomb: Int
    private var keyRun1: Int
    private var keyRun2: Int
    private var keyStart: Int
    private var mPrefsDialog: Dialog? = null
    private var captureKey = CaptureKey.NONE
    private var mHeldTriggerL = false
    private var mHeldTriggerR = false
    private var mPhysicalJoystickAxisValueX = 0.0f
    private var mPhysicalJoystickAxisValueY = 0.0f
    private var mPhysicalJoystickDPadValueX = 0.0f
    private var mPhysicalJoystickDPadValueY = 0.0f
    private var mPhysicalDPadDownVal = 0.0f
    private var mPhysicalDPadUpVal = 0.0f
    private var mPhysicalDPadLeftVal = 0.0f
    private var mPhysicalDPadRightVal = 0.0f
    fun onClosing() {
        // if we've got a dialog open, kill it
        if (mPrefsDialog != null && mPrefsDialog!!.isShowing) {
            mPrefsDialog!!.cancel()
        }
    }

    private fun savePrefs() {
        // save this to prefs
        val preferences =
            gamePadActivity.getSharedPreferences("BSRemotePrefs", Context.MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putFloat("buttonScale", buttonScale)
        editor.putFloat("dPadScale", dPadScale)
        editor.putFloat("dPadOffsX", dPadOffsX)
        editor.putFloat("dPadOffsY", dPadOffsY)
        editor.putFloat("buttonOffsX", buttonOffsX)
        editor.putFloat("buttonOffsY", buttonOffsY)
        editor.putString("dPadType", dPadType)
        editor.putInt("keyPickUp", keyPickUp)
        editor.putInt("keyJump", keyJump)
        editor.putInt("keyPunch", keyPunch)
        editor.putInt("keyBomb", keyBomb)
        editor.putInt("keyRun1", keyRun1)
        editor.putInt("keyRun2", keyRun2)
        editor.putInt("keyStart", keyStart)
        editor.apply()
    }

    private fun updateSizes() {

        // update button positions and whatnot
        val ratio = width.toFloat() / height
        val height = 1.0f / ratio
        val bWidth = 0.08f * buttonScale
        val bHeight = 0.08f * buttonScale
        buttonCenterX = 0.95f - 0.1f * buttonScale + buttonOffsX * 0.2f
        buttonCenterY = height * 0.6f - 0.0f * buttonScale - buttonOffsY * 0.3f
        dPadCenterX = 0.0f + 0.1f * dPadScale + dPadOffsX * 0.2f
        dPadCenterY = height * 0.6f - 0.0f * dPadScale - dPadOffsY * 0.3f
        val bSep = 0.1f * buttonScale
        gl.quitButtonX = 0.06f * buttonScale
        gl.quitButtonY = 0.035f * buttonScale
        gl.quitButtonWidth = 0.1f * buttonScale
        gl.quitButtonHeight = 0.05f * buttonScale
        gl.prefsButtonX = 0.17f * buttonScale
        gl.prefsButtonY = 0.035f * buttonScale
        gl.prefsButtonWidth = 0.1f * buttonScale
        gl.prefsButtonHeight = 0.05f * buttonScale
        gl.startButtonX = 0.28f * buttonScale
        gl.startButtonY = 0.035f * buttonScale
        gl.startButtonWidth = 0.1f * buttonScale
        gl.startButtonHeight = 0.05f * buttonScale
        gl.throwButtonX = buttonCenterX
        gl.throwButtonY = buttonCenterY - bSep
        gl.throwButtonWidth = bWidth
        gl.throwButtonHeight = bHeight
        gl.punchButtonX = buttonCenterX - bSep
        gl.punchButtonY = buttonCenterY
        gl.punchButtonWidth = bWidth
        gl.punchButtonHeight = bHeight
        gl.bombButtonX = buttonCenterX + bSep
        gl.bombButtonY = buttonCenterY
        gl.bombButtonWidth = bWidth
        gl.bombButtonHeight = bHeight
        gl.jumpButtonX = buttonCenterX
        gl.jumpButtonY = buttonCenterY + bSep
        gl.jumpButtonWidth = bWidth
        gl.jumpButtonHeight = bHeight
        gl.joystickCenterX = dPadCenterX
        gl.joystickCenterY = dPadCenterY
        gl.joystickWidth = 0.2f * dPadScale
        gl.joystickHeight = 0.2f * dPadScale
        gl.joystickX = dPadCenterX
        gl.joystickY = dPadCenterY
        if (!initialized) {
            initialized = true
        }
    }

    public override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateSizes()
    }

    private fun pointInBox(
        x: Float,
        y: Float,
        bx: Float,
        by: Float,
        sx: Float,
        sy: Float,
    ): Boolean {
        return x >= bx - 0.5f * sx && x <= bx + 0.5 * sx && y >= by - 0.5 * sy && y <= by + 0.5 * sy
    }

    fun updateButtonsForTouches(event: MotionEvent) {
        var punchHeld = false
        var throwHeld = false
        var jumpHeld = false
        var bombHeld = false
        val mult = 1.0f / width
        val pointerCount = event.pointerCount
        for (i in 0 until pointerCount) {

            // ignore touch-up events
            val actionPointerIndex = event.actionIndex
            val action = event.actionMasked
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                && i == actionPointerIndex
            ) {
                continue
            }
            val touch = event.getPointerId(i)

            // ignore dpad touch
            if (touch == dPadTouch) {
                continue
            }
            val s = 4.0f / buttonScale
            // get the point in button-center-coords
            val x = event.getX(i) * mult
            val y = event.getY(i) * mult
            val bx = (x - buttonCenterX) * s
            val by = (y - buttonCenterY) * s
            val threshold = 0.3f
            var pbx: Float
            var pby: Float
            var len: Float
            var punchLen: Float
            var jumpLen: Float
            var throwLen: Float
            var bombLen: Float

            // punch
            pbx = (x - gl.punchButtonX) * s
            pby = (y - gl.punchButtonY) * s
            len = Math.sqrt((pbx * pbx + pby * pby).toDouble()).toFloat()
            punchLen = len
            if (len < threshold) {
                punchHeld = true
            }

            // throw
            pbx = (x - gl.throwButtonX) * s
            pby = (y - gl.throwButtonY) * s
            len = Math.sqrt((pbx * pbx + pby * pby).toDouble()).toFloat()
            throwLen = len
            if (len < threshold) {
                throwHeld = true
            }

            // jump
            pbx = (x - gl.jumpButtonX) * s
            pby = (y - gl.jumpButtonY) * s
            len = Math.sqrt((pbx * pbx + pby * pby).toDouble()).toFloat()
            jumpLen = len
            if (len < threshold) {
                jumpHeld = true
            }

            // bomb
            pbx = (x - gl.bombButtonX) * s
            pby = (y - gl.bombButtonY) * s
            len = Math.sqrt((pbx * pbx + pby * pby).toDouble()).toFloat()
            bombLen = len
            if (len < threshold) {
                bombHeld = true
            }

            // how much larger than the button/dpad areas we should count touch
            // events in
            val buttonBuffer = 2.0f
            // ok now lets take care of fringe areas and non-moved touches
            // a touch in our button area should *always* affect at least one
            // button
            // ..so lets find the closest button && press it.
            // this will probably coincide with what we just set above but thats
            // ok.
            if (x > 0.5 && bx > -1.0 * buttonBuffer && bx < 1.0 * buttonBuffer && by > -1.0 * buttonBuffer && by < 1.0 * buttonBuffer) {
                if (punchLen < throwLen && punchLen < jumpLen && punchLen < bombLen) {
                    punchHeld = true
                } else if (throwLen < punchLen && throwLen < jumpLen && throwLen < bombLen) {
                    throwHeld = true
                } else if (jumpLen < punchLen && jumpLen < throwLen && jumpLen < bombLen) {
                    jumpHeld = true
                } else {
                    bombHeld = true
                }
            }
        }
        val throwWasHeld =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_THROW != 0
        val jumpWasHeld =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_JUMP != 0
        val punchWasHeld =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_PUNCH != 0
        val bombWasHeld =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_BOMB != 0

        // send press events for non-held ones we're now over
        if (!throwWasHeld && throwHeld) {
            handleThrowPress()
        }
        if (throwWasHeld && !throwHeld) {
            handleThrowRelease()
        }

        // send press events for non-held ones we're now over
        if (!punchWasHeld && punchHeld) {
            handlePunchPress()
        }
        if (punchWasHeld && !punchHeld) {
            handlePunchRelease()
        }

        // send press events for non-held ones we're now over
        if (!bombWasHeld && bombHeld) {
            handleBombPress()
        }
        if (bombWasHeld && !bombHeld) {
            handleBombRelease()
        }

        // send press events for non-held ones we're now over
        if (!jumpWasHeld && jumpHeld) {
            handleJumpPress()
        }
        if (jumpWasHeld && !jumpHeld) {
            handleJumpRelease()
        }
    }

    private fun handleMenuPress() {
        gamePadActivity.buttonStateV1 =
            gamePadActivity.buttonStateV1 or GamePadActivity.BS_REMOTE_STATE_MENU
        gamePadActivity.buttonStateV2 =
            gamePadActivity.buttonStateV2 or GamePadActivity.BS_REMOTE_STATE2_MENU
        gamePadActivity.doStateChange(false)
        gl.startButtonPressed = true
    }

    private fun handleMenuRelease() {
        gamePadActivity.buttonStateV1 =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_MENU.inv()
        gamePadActivity.buttonStateV2 =
            gamePadActivity.buttonStateV2 and GamePadActivity.BS_REMOTE_STATE2_MENU.inv()
        gamePadActivity.doStateChange(false)
        gl.startButtonPressed = false
    }

    private fun handleRunPress() {
        gamePadActivity.buttonStateV2 =
            gamePadActivity.buttonStateV2 or GamePadActivity.BS_REMOTE_STATE2_RUN
        gamePadActivity.doStateChange(false)
    }

    private fun handleRunRelease() {
        gamePadActivity.buttonStateV2 =
            gamePadActivity.buttonStateV2 and GamePadActivity.BS_REMOTE_STATE2_RUN.inv()
        gamePadActivity.doStateChange(false)
    }

    private fun handleThrowPress() {
        gamePadActivity.buttonStateV1 =
            gamePadActivity.buttonStateV1 or GamePadActivity.BS_REMOTE_STATE_THROW
        gamePadActivity.buttonStateV2 =
            gamePadActivity.buttonStateV2 or GamePadActivity.BS_REMOTE_STATE2_THROW
        gamePadActivity.doStateChange(false)
        gl.throwPressed = true
    }

    private fun handleThrowRelease() {
        gamePadActivity.buttonStateV1 =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_THROW.inv()
        gamePadActivity.buttonStateV2 =
            gamePadActivity.buttonStateV2 and GamePadActivity.BS_REMOTE_STATE2_THROW.inv()
        gamePadActivity.doStateChange(false)
        gl.throwPressed = false
    }

    private fun handleBombPress() {
        gamePadActivity.buttonStateV1 =
            gamePadActivity.buttonStateV1 or GamePadActivity.BS_REMOTE_STATE_BOMB
        gamePadActivity.buttonStateV2 =
            gamePadActivity.buttonStateV2 or GamePadActivity.BS_REMOTE_STATE2_BOMB
        gamePadActivity.doStateChange(false)
        gl.bombPressed = true
    }

    private fun handleBombRelease() {
        gamePadActivity.buttonStateV1 =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_BOMB.inv()
        gamePadActivity.buttonStateV2 =
            gamePadActivity.buttonStateV2 and GamePadActivity.BS_REMOTE_STATE2_BOMB.inv()
        gamePadActivity.doStateChange(false)
        gl.bombPressed = false
    }

    private fun handleJumpPress() {
        gamePadActivity.buttonStateV1 =
            gamePadActivity.buttonStateV1 or GamePadActivity.BS_REMOTE_STATE_JUMP
        gamePadActivity.buttonStateV2 =
            gamePadActivity.buttonStateV2 or GamePadActivity.BS_REMOTE_STATE2_JUMP
        gamePadActivity.doStateChange(false)
        gl.jumpPressed = true
    }

    private fun handleJumpRelease() {
        gamePadActivity.buttonStateV1 =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_JUMP.inv()
        gamePadActivity.buttonStateV2 =
            gamePadActivity.buttonStateV2 and GamePadActivity.BS_REMOTE_STATE2_JUMP.inv()
        gamePadActivity.doStateChange(false)
        gl.jumpPressed = false
    }

    private fun handlePunchPress() {
        gamePadActivity.buttonStateV1 =
            gamePadActivity.buttonStateV1 or GamePadActivity.BS_REMOTE_STATE_PUNCH
        gamePadActivity.buttonStateV2 =
            gamePadActivity.buttonStateV2 or GamePadActivity.BS_REMOTE_STATE2_PUNCH
        gamePadActivity.doStateChange(false)
        gl.punchPressed = true
    }

    private fun handlePunchRelease() {
        gamePadActivity.buttonStateV1 =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_PUNCH.inv()
        gamePadActivity.buttonStateV2 =
            gamePadActivity.buttonStateV2 and GamePadActivity.BS_REMOTE_STATE2_PUNCH.inv()
        gamePadActivity.doStateChange(false)
        gl.punchPressed = false
    }

    fun doPrefs() {
        val d = Dialog(gamePadActivity)
        mPrefsDialog = d
        d.setContentView(R.layout.prefs)
        d.setCanceledOnTouchOutside(true)
        var seekbar: SeekBar
        seekbar = d.findViewById(R.id.seekBarButtonSize)
        seekbar.progress = (100.0f * (buttonScale - sizeMin) / (sizeMax - sizeMin)).toInt()
        seekbar.setOnSeekBarChangeListener(
            object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    buttonScale = sizeMin + (sizeMax - sizeMin) * (progress / 100.0f)
                    updateSizes()
                    requestRender()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    savePrefs()
                }
            })
        seekbar = d.findViewById(R.id.seekBarDPadSize)
        seekbar.progress = (100.0f * (dPadScale - sizeMin) / (sizeMax - sizeMin)).toInt()
        seekbar.setOnSeekBarChangeListener(
            object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    dPadScale = sizeMin + (sizeMax - sizeMin) * (progress / 100.0f)
                    updateSizes()
                    requestRender()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    savePrefs()
                }
            })
        seekbar = d.findViewById(R.id.seekBarButtonPosition1)
        seekbar.progress =
            (100.0f * (buttonOffsX - buttonOffsXMin) / (buttonOffsXMax - buttonOffsXMin)).toInt()
        seekbar.setOnSeekBarChangeListener(
            object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    buttonOffsX =
                        buttonOffsXMin + (buttonOffsXMax - buttonOffsXMin) * (progress / 100.0f)
                    updateSizes()
                    requestRender()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    savePrefs()
                }
            })
        seekbar = d.findViewById(R.id.seekBarButtonPosition2)
        seekbar.progress =
            (100.0f * (buttonOffsY - buttonOffsYMin) / (buttonOffsYMax - buttonOffsYMin)).toInt()
        seekbar.setOnSeekBarChangeListener(
            object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    buttonOffsY =
                        buttonOffsYMin + (buttonOffsYMax - buttonOffsYMin) * (progress / 100.0f)
                    updateSizes()
                    requestRender()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    savePrefs()
                }
            })
        seekbar = d.findViewById(R.id.seekBarDPadPosition1)
        seekbar.progress =
            (100.0f * (dPadOffsX - dPadOffsXMin) / (dPadOffsXMax - dPadOffsXMin)).toInt()
        seekbar.setOnSeekBarChangeListener(
            object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    dPadOffsX = dPadOffsXMin + (dPadOffsXMax - dPadOffsXMin) * (progress / 100.0f)
                    updateSizes()
                    requestRender()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    savePrefs()
                }
            })
        seekbar = d.findViewById(R.id.seekBarDPadPosition2)
        seekbar.progress =
            (100.0f * (dPadOffsY - dPadOffsYMin) / (dPadOffsYMax - dPadOffsYMin)).toInt()
        seekbar.setOnSeekBarChangeListener(
            object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    dPadOffsY = dPadOffsYMin + (dPadOffsYMax - dPadOffsYMin) * (progress / 100.0f)
                    updateSizes()
                    requestRender()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    savePrefs()
                }
            })
        val dPadFloatingButton = d.findViewById<RadioButton>(R.id.radioButtonDPadFloating)
        val dPadFixedButton = d.findViewById<RadioButton>(R.id.radioButtonDPadFixed)
        if (dPadType == "floating") {
            dPadFloatingButton.isChecked = true
        } else {
            dPadFixedButton.isChecked = true
        }
        dPadFloatingButton.setOnClickListener { v: View? ->
            dPadType = "floating"
            savePrefs()
        }
        dPadFixedButton.setOnClickListener { v: View? ->
            dPadType = "fixed"
            savePrefs()
        }
        val configHardwareButton = d.findViewById<Button>(R.id.buttonConfigureHardwareButtons)
        configHardwareButton.setOnClickListener { v: View? ->
            // kill this dialog and bring up the hardware one
            d.cancel()
            doHardwareControlsPrefs()
        }
        d.setTitle(R.string.settings)
        d.show()
    }

    fun getPrettyKeyName(keyCode: Int): String {
        var `val` = KeyEvent.keyCodeToString(keyCode)
        if (`val`.startsWith("KEYCODE_")) {
            `val` = `val`.replace("KEYCODE_".toRegex(), "")
        }
        if (`val`.startsWith("BUTTON_")) {
            `val` = `val`.replace("BUTTON_".toRegex(), "")
        }
        return `val`
    }

    fun doCaptureKey(): Dialog {
        val d = Dialog(gamePadActivity)
        d.setContentView(R.layout.prefs_capture_key)
        d.setCanceledOnTouchOutside(true)
        d.setOnKeyListener { arg0: DialogInterface?, keyCode: Int, event: KeyEvent? ->
            setActionKey(keyCode)
            d.dismiss()
            true
        }
        val b = d.findViewById<Button>(R.id.buttonResetToDefault)
        b.setOnClickListener { v: View? ->
            when (captureKey) {
                CaptureKey.PICK_UP -> setActionKey(KeyEvent.KEYCODE_BUTTON_Y)
                CaptureKey.JUMP -> setActionKey(KeyEvent.KEYCODE_BUTTON_A)
                CaptureKey.PUNCH -> setActionKey(KeyEvent.KEYCODE_BUTTON_X)
                CaptureKey.BOMB -> setActionKey(KeyEvent.KEYCODE_BUTTON_B)
                CaptureKey.RUN1 -> setActionKey(KeyEvent.KEYCODE_BUTTON_L1)
                CaptureKey.RUN2 -> setActionKey(KeyEvent.KEYCODE_BUTTON_R1)
                CaptureKey.START -> setActionKey(KeyEvent.KEYCODE_BUTTON_START)
                else -> log("Error: unrecognized key in doActionKey", null, d.context)
            }
            d.dismiss()
        }
        d.setTitle(R.string.capturing)
        d.show()
        return d
    }

    private fun setActionKey(keyval: Int) {
        when (captureKey) {
            CaptureKey.PICK_UP -> keyPickUp = keyval
            CaptureKey.JUMP -> keyJump = keyval
            CaptureKey.PUNCH -> keyPunch = keyval
            CaptureKey.BOMB -> keyBomb = keyval
            CaptureKey.RUN1 -> keyRun1 = keyval
            CaptureKey.RUN2 -> keyRun2 = keyval
            CaptureKey.START -> keyStart = keyval
            else -> log("Error: unrecognized key in setActionKey", null, context)
        }
        savePrefs()
    }

    private fun updateHardwareControlsLabels(d: Dialog) {
        var t = d.findViewById<TextView>(R.id.textPickUp)
        t.text = getPrettyKeyName(keyPickUp)
        t = d.findViewById(R.id.textJump)
        t.text = getPrettyKeyName(keyJump)
        t = d.findViewById(R.id.textPunch)
        t.text = getPrettyKeyName(keyPunch)
        t = d.findViewById(R.id.textBomb)
        t.text = getPrettyKeyName(keyBomb)
        t = d.findViewById(R.id.textRun1)
        t.text = getPrettyKeyName(keyRun1)
        t = d.findViewById(R.id.textRun2)
        t.text = getPrettyKeyName(keyRun2)
        t = d.findViewById(R.id.textStart)
        t.text = getPrettyKeyName(keyStart)
    }

    fun doHardwareControlsPrefs() {
        val d = Dialog(gamePadActivity)
        d.setContentView(R.layout.prefs_hardware_controls)
        d.setCanceledOnTouchOutside(true)
        val l = OnClickListener { v: View ->

            // take note of which action this capture will apply to, then launch
            // a capture..
            if (v === d.findViewById<View>(R.id.buttonPickUp)) {
                captureKey = CaptureKey.PICK_UP
            } else if (v === d.findViewById<View>(R.id.buttonJump)) {
                captureKey = CaptureKey.JUMP
            } else if (v === d.findViewById<View>(R.id.buttonPunch)) {
                captureKey = CaptureKey.PUNCH
            } else if (v === d.findViewById<View>(R.id.buttonBomb)) {
                captureKey = CaptureKey.BOMB
            } else if (v === d.findViewById<View>(R.id.buttonRun1)) {
                captureKey = CaptureKey.RUN1
            } else if (v === d.findViewById<View>(R.id.buttonRun2)) {
                captureKey = CaptureKey.RUN2
            } else if (v === d.findViewById<View>(R.id.buttonStart)) {
                captureKey = CaptureKey.START
            } else {
                log("Error: unrecognized capture button", null, context)
            }
            val d2 = doCaptureKey()
            d2.setOnDismissListener { dialog: DialogInterface? -> updateHardwareControlsLabels(d) }
        }
        d.findViewById<View>(R.id.buttonPickUp).setOnClickListener(l)
        d.findViewById<View>(R.id.buttonJump).setOnClickListener(l)
        d.findViewById<View>(R.id.buttonPunch).setOnClickListener(l)
        d.findViewById<View>(R.id.buttonBomb).setOnClickListener(l)
        d.findViewById<View>(R.id.buttonRun1).setOnClickListener(l)
        d.findViewById<View>(R.id.buttonRun2).setOnClickListener(l)
        d.findViewById<View>(R.id.buttonStart).setOnClickListener(l)
        d.setTitle(R.string.configHardwareButtons)
        updateHardwareControlsLabels(d)
        d.show()
    }

    // Generic-motion events
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        var handled = false
        var changed = false
        if (event.source and InputDevice.SOURCE_CLASS_JOYSTICK != 0) {
            val historySize = event.historySize
            // Append all historical values in the batch.
            for (historyPos in 0 until historySize + 1) {
                var valueAxisX: Float
                var valueAxisY: Float
                var valueDPadX: Float
                var valueDPadY: Float
                var valueTriggerL: Float
                var valueTriggerR: Float

                // go through historical values and current
                if (historyPos < historySize) {
                    valueAxisX = event.getHistoricalAxisValue(MotionEvent.AXIS_X, historyPos)
                    valueAxisY = event.getHistoricalAxisValue(MotionEvent.AXIS_Y, historyPos)
                    valueDPadX = event.getHistoricalAxisValue(MotionEvent.AXIS_HAT_X, historyPos)
                    valueDPadY = event.getHistoricalAxisValue(MotionEvent.AXIS_HAT_Y, historyPos)
                    valueTriggerL =
                        event.getHistoricalAxisValue(MotionEvent.AXIS_LTRIGGER, historyPos)
                    valueTriggerR =
                        event.getHistoricalAxisValue(MotionEvent.AXIS_RTRIGGER, historyPos)
                } else {
                    valueAxisX = event.getAxisValue(MotionEvent.AXIS_X)
                    valueAxisY = event.getAxisValue(MotionEvent.AXIS_Y)
                    valueDPadX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
                    valueDPadY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
                    valueTriggerL = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
                    valueTriggerR = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
                }
                val triggerHeldL = valueTriggerL >= 0.5
                val triggerHeldR = valueTriggerR >= 0.5

                // handle trigger state changes
                if (triggerHeldL != mHeldTriggerL || triggerHeldR != mHeldTriggerR) {
                    val runWasHeld = !mHeldKeys.isEmpty() || mHeldTriggerL || mHeldTriggerR
                    mHeldTriggerL = triggerHeldL
                    mHeldTriggerR = triggerHeldR
                    val runIsHeld = !mHeldKeys.isEmpty() || mHeldTriggerL || mHeldTriggerR
                    if (!runWasHeld) {
                        // if (!runWasHeld && runIsHeld) {
                        // (logically runIsHeld is always true here; shutting up lint)
                        handleRunPress()
                    }
                    if (runWasHeld && !runIsHeld) {
                        handleRunRelease()
                    }
                    changed = true
                }

                // handle dpad state changes
                if (dPadTouch == -1
                    && (valueAxisX != mPhysicalJoystickAxisValueX || valueAxisY != mPhysicalJoystickAxisValueY || valueDPadX != mPhysicalJoystickDPadValueX || valueDPadY != mPhysicalJoystickDPadValueY)
                ) {
                    var valueX: Float
                    var valueY: Float
                    var valid: Boolean
                    // if our dpad has changed, use that value..
                    if (valueDPadX != mPhysicalJoystickDPadValueX
                        || valueDPadY != mPhysicalJoystickDPadValueY
                    ) {
                        valueX = valueDPadX
                        valueY = valueDPadY
                        valid = true
                    } else {
                        // otherwise, use the normal axis value *unless* we've got a dpad
                        // press going on
                        // (wanna avoid having analog axis noise wipe out dpad presses)
                        valueX = valueAxisX
                        valueY = valueAxisY
                        valid = (Math.abs(mPhysicalJoystickDPadValueX) < 0.1
                                && Math.abs(mPhysicalJoystickDPadValueY) < 0.1)
                    }
                    mPhysicalJoystickAxisValueX = valueAxisX
                    mPhysicalJoystickAxisValueY = valueAxisY
                    mPhysicalJoystickDPadValueX = valueDPadX
                    mPhysicalJoystickDPadValueY = valueDPadY
                    if (valid) {
                        val s = 30.0f / dPadScale
                        if (valueX < -1.0f) {
                            valueX = -1.0f
                        } else if (valueX > 1.0f) {
                            valueX = 1.0f
                        }
                        if (valueY < -1.0f) {
                            valueY = -1.0f
                        } else if (valueY > 1.0f) {
                            valueY = 1.0f
                        }
                        gl.joystickX = gl.joystickCenterX + valueX / s
                        gl.joystickY = gl.joystickCenterY + valueY / s
                        gamePadActivity.dPadStateH = valueX
                        gamePadActivity.dPadStateV = valueY
                        gamePadActivity.doStateChange(false)
                        changed = true
                    }
                }
                handled = true
            }
        }
        if (changed) {
            requestRender()
        }
        return handled
    }

    private fun handlePhysicalDPadEvent() {
        var valueX = mPhysicalDPadRightVal - mPhysicalDPadLeftVal
        var valueY = mPhysicalDPadDownVal - mPhysicalDPadUpVal
        val s = 30.0f / dPadScale
        if (valueX < -1.0f) {
            valueX = -1.0f
        } else if (valueX > 1.0f) {
            valueX = 1.0f
        }
        if (valueY < -1.0f) {
            valueY = -1.0f
        } else if (valueY > 1.0f) {
            valueY = 1.0f
        }
        gl.joystickX = gl.joystickCenterX + valueX / s
        gl.joystickY = gl.joystickCenterY + valueY / s
        gamePadActivity.dPadStateH = valueX
        gamePadActivity.dPadStateV = valueY
        gamePadActivity.doStateChange(false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val throwWasHeld =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_THROW != 0
        val jumpWasHeld =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_JUMP != 0
        val punchWasHeld =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_PUNCH != 0
        val bombWasHeld =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_BOMB != 0
        val menuWasHeld =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_MENU != 0
        var handled = false

        // check for custom assigned keys:
        if (keyCode == keyPickUp) {
            if (!throwWasHeld) {
                handleThrowPress()
            }
            handled = true
        } else if (keyCode == keyJump) {
            if (!jumpWasHeld) {
                handleJumpPress()
            }
            handled = true
        } else if (keyCode == keyPunch) {
            if (!punchWasHeld) {
                handlePunchPress()
            }
            handled = true
        } else if (keyCode == keyBomb) {
            if (!bombWasHeld) {
                handleBombPress()
            }
            handled = true
        } else if (keyCode == keyStart) {
            if (!menuWasHeld) {
                handleMenuPress()
            }
            handled = true
        } else if (keyCode == keyRun1 || keyCode == keyRun2) {
            val runWasHeld = !mHeldKeys.isEmpty() || mHeldTriggerL || mHeldTriggerR
            mHeldKeys.add(keyCode)
            val runIsHeld = !mHeldKeys.isEmpty() || mHeldTriggerL || mHeldTriggerR
            if (!runWasHeld && runIsHeld) {
                handleRunPress()
            }
            handled = true
        } else {
            // resort to hard-coded defaults..
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    mPhysicalDPadUpVal = 1.0f
                    handlePhysicalDPadEvent()
                    handled = true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    mPhysicalDPadDownVal = 1.0f
                    handlePhysicalDPadEvent()
                    handled = true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    mPhysicalDPadLeftVal = 1.0f
                    handlePhysicalDPadEvent()
                    handled = true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    mPhysicalDPadRightVal = 1.0f
                    handlePhysicalDPadEvent()
                    handled = true
                }
                KeyEvent.KEYCODE_BUTTON_A -> {
                    if (!jumpWasHeld) {
                        handleJumpPress()
                    }
                    handled = true
                }
                KeyEvent.KEYCODE_BUTTON_B -> {
                    if (!bombWasHeld) {
                        handleBombPress()
                    }
                    handled = true
                }
                KeyEvent.KEYCODE_BUTTON_X -> {
                    if (!punchWasHeld) {
                        handlePunchPress()
                    }
                    handled = true
                }
                KeyEvent.KEYCODE_BUTTON_Y -> {
                    if (!throwWasHeld) {
                        handleThrowPress()
                    }
                    handled = true
                }
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_START -> {
                    if (!menuWasHeld) {
                        handleMenuPress()
                    }
                    handled = true
                }
                else -> if (isRunKey(keyCode)) {
                    val runWasHeld = !mHeldKeys.isEmpty() || mHeldTriggerL || mHeldTriggerR
                    mHeldKeys.add(keyCode)
                    val runIsHeld = !mHeldKeys.isEmpty() || mHeldTriggerL || mHeldTriggerR
                    if (!runWasHeld && runIsHeld) {
                        handleRunPress()
                    }
                    handled = true
                }
            }
        }
        if (handled) {
            requestRender()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun isRunKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> true
            else -> false
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val throwWasHeld =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_THROW != 0
        val jumpWasHeld =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_JUMP != 0
        val punchWasHeld =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_PUNCH != 0
        val bombWasHeld =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_BOMB != 0
        val menuWasHeld =
            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_MENU != 0
        var handled = false

        // handle our custom-assigned keys
        if (keyCode == keyPickUp) {
            if (throwWasHeld) {
                handleThrowRelease()
            }
            handled = true
        } else if (keyCode == keyJump) {
            if (jumpWasHeld) {
                handleJumpRelease()
            }
            handled = true
        } else if (keyCode == keyPunch) {
            if (punchWasHeld) {
                handlePunchRelease()
            }
            handled = true
        } else if (keyCode == keyBomb) {
            if (bombWasHeld) {
                handleBombRelease()
            }
            handled = true
        } else if (keyCode == keyStart) {
            if (menuWasHeld) {
                handleMenuRelease()
            }
            handled = true
        } else if (keyCode == keyRun1 || keyCode == keyRun2) {
            val runWasHeld = !mHeldKeys.isEmpty() || mHeldTriggerL || mHeldTriggerR
            mHeldKeys.remove(keyCode)
            val runIsHeld = !mHeldKeys.isEmpty() || mHeldTriggerL || mHeldTriggerR
            if (runWasHeld && !runIsHeld) {
                handleRunRelease()
            }
            handled = true
        } else {
            // fall back on hard-coded defaults
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    mPhysicalDPadUpVal = 0.0f
                    handlePhysicalDPadEvent()
                    handled = true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    mPhysicalDPadDownVal = 0.0f
                    handlePhysicalDPadEvent()
                    handled = true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    mPhysicalDPadLeftVal = 0.0f
                    handlePhysicalDPadEvent()
                    handled = true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    mPhysicalDPadRightVal = 0.0f
                    handlePhysicalDPadEvent()
                    handled = true
                }
                KeyEvent.KEYCODE_BUTTON_A -> {
                    if (jumpWasHeld) {
                        handleJumpRelease()
                    }
                    handled = true
                }
                KeyEvent.KEYCODE_BUTTON_B -> {
                    if (bombWasHeld) {
                        handleBombRelease()
                    }
                    handled = true
                }
                KeyEvent.KEYCODE_BUTTON_X -> {
                    if (punchWasHeld) {
                        handlePunchRelease()
                    }
                    handled = true
                }
                KeyEvent.KEYCODE_BUTTON_Y -> {
                    if (throwWasHeld) {
                        handleThrowRelease()
                    }
                    handled = true
                }
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_START -> {
                    if (menuWasHeld) {
                        handleMenuRelease()
                    }
                    handled = true
                }
                else -> if (isRunKey(keyCode)) {
                    val runWasHeld = !mHeldKeys.isEmpty() || mHeldTriggerL || mHeldTriggerR
                    mHeldKeys.remove(keyCode)
                    val runIsHeld = !mHeldKeys.isEmpty() || mHeldTriggerL || mHeldTriggerR
                    if (runWasHeld && !runIsHeld) {
                        handleRunRelease()
                    }
                    handled = true
                }
            }
        }
        if (handled) {
            requestRender()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // Touch events
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointerCount = event.pointerCount
        val actionPointerIndex = event.actionIndex
        val action = event.actionMasked
        val mult = 1.0f / width

        // touch margin
        val tm = 1.4f
        val tm2 = 2.4f
        for (i in 0 until pointerCount) {
            val x = event.getX(i) * mult
            val y = event.getY(i) * mult
            val fingerID = event.getPointerId(i)
            if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)
                && actionPointerIndex == i
            ) {
                if (pointInBox(
                        x,
                        y,
                        gl.prefsButtonX,
                        gl.prefsButtonY,
                        gl.prefsButtonWidth * tm,
                        gl.prefsButtonHeight * tm2)
                ) {
                    // prefs presses
                    prefsTouch = fingerID
                    prefsHover = true
                    gl.prefsButtonPressed = true
                } else if (pointInBox(
                        x,
                        y,
                        gl.quitButtonX,
                        gl.quitButtonY,
                        gl.quitButtonWidth * tm,
                        gl.quitButtonHeight * tm2)
                ) {
                    // check for a quit press
                    quitTouch = fingerID
                    quitHover = true
                    gl.quitButtonPressed = true
                } else if (pointInBox(
                        x,
                        y,
                        gl.startButtonX,
                        gl.startButtonY,
                        gl.startButtonWidth * tm,
                        gl.startButtonHeight * tm2)
                ) {
                    // check for a start press
                    startTouch = fingerID
                    startHover = true
                    gl.startButtonPressed = true
                } else if (x < 0.5) {
                    // check for a dpad touch
                    dPadTouch = fingerID
                    // in fixed joystick mode we want touches to count towards
                    // joystick motion immediately; not just after they move
                    dPadTouchIsMove = dPadType == "fixed"
                    dPadTouchStartX = x
                    dPadTouchStartY = y
                    gl.joystickX = x
                    gl.joystickY = y
                    gl.thumbPressed = true
                }
            }
            // handle existing button touches
            if (fingerID == quitTouch) {
                // update position
                gl.quitButtonPressed = pointInBox(
                    x,
                    y,
                    gl.quitButtonX,
                    gl.quitButtonY,
                    gl.quitButtonWidth * tm,
                    gl.quitButtonHeight * tm2)
                quitHover = gl.quitButtonPressed
                // if the touch is ending...
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                    && actionPointerIndex == i
                ) {
                    quitTouch = -1
                    // _quitHover = false;
                    gl.quitButtonPressed = false
                    if (quitHover) {

                        // ewwww - seeing that in some cases our onDestroy()
                        // doesn't get called for a while which keeps the server
                        // from announcing our departure.  lets just shoot off
                        // one random disconnect packet here to hopefully speed that along.
                        gamePadActivity.sendDisconnectPacket()
                        gamePadActivity.finish()
                    }
                }
            } else if (fingerID == prefsTouch) {
                // update position
                gl.prefsButtonPressed = pointInBox(
                    x,
                    y,
                    gl.prefsButtonX,
                    gl.prefsButtonY,
                    gl.prefsButtonWidth * tm,
                    gl.prefsButtonHeight * tm2)
                prefsHover = gl.prefsButtonPressed
                // if the touch is ending...
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                    && actionPointerIndex == i
                ) {
                    prefsTouch = -1
                    gl.prefsButtonPressed = false
                    if (prefsHover) {
                        doPrefs()
                    }
                }
            } else if (fingerID == startTouch) {
                // update position
                gl.startButtonPressed = pointInBox(
                    x,
                    y,
                    gl.startButtonX,
                    gl.startButtonY,
                    gl.startButtonWidth * tm,
                    gl.startButtonHeight * tm2)
                startHover = gl.startButtonPressed
                // if the touch is ending...
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                    && actionPointerIndex == i
                ) {
                    startTouch = -1
                    gl.startButtonPressed = false
                    if (startHover) {
                        // send 2 state-changes (start-down and start-up)
                        gamePadActivity.buttonStateV1 =
                            gamePadActivity.buttonStateV1 or GamePadActivity.BS_REMOTE_STATE_MENU
                        gamePadActivity.buttonStateV2 =
                            gamePadActivity.buttonStateV2 or GamePadActivity.BS_REMOTE_STATE2_MENU
                        gamePadActivity.doStateChange(false)
                        gamePadActivity.buttonStateV1 =
                            gamePadActivity.buttonStateV1 and GamePadActivity.BS_REMOTE_STATE_MENU.inv()
                        gamePadActivity.buttonStateV2 =
                            gamePadActivity.buttonStateV2 and GamePadActivity.BS_REMOTE_STATE2_MENU.inv()
                        gamePadActivity.doStateChange(false)
                    }
                }
            }

            // if its our existing dpad-touch
            if (fingerID == dPadTouch) {

                // if we've moved away from the initial touch position we no longer
                // consider it for a direction tap
                if (!dPadTouchIsMove
                    && (Math.abs(x - dPadTouchStartX) > 0.01 || Math.abs(y - dPadTouchStartY) > 0.01)
                ) {
                    dPadTouchIsMove = true
                    gl.joystickCenterX = dPadTouchStartX
                    gl.joystickCenterY = dPadTouchStartY
                }
                // if its moved, pass it along as a joystick event
                if (dPadTouchIsMove) {
                    val s = 30.0f / dPadScale
                    val xVal = (x - gl.joystickCenterX) * s
                    val yVal = (y - gl.joystickCenterY) * s
                    var xValClamped = xVal
                    var yValClamped = yVal

                    // keep our H/V values within a unit box
                    // (originally I clamped length to 1 but as a result our diagonal
                    // running speed was less than other analog controllers..)
                    if (xValClamped > 1.0f) {
                        val m = 1.0f / xValClamped
                        xValClamped *= m
                        yValClamped *= m
                    } else if (xValClamped < -1.0f) {
                        val m = -1.0f / xValClamped
                        xValClamped *= m
                        yValClamped *= m
                    }
                    if (yValClamped > 1.0f) {
                        val m = 1.0f / yValClamped
                        xValClamped *= m
                        yValClamped *= m
                    } else if (yValClamped < -1.0f) {
                        val m = -1.0f / yValClamped
                        xValClamped *= m
                        yValClamped *= m
                    }
                    gl.joystickX = gl.joystickCenterX + xVal / s
                    gl.joystickY = gl.joystickCenterY + yVal / s

                    // if its moved far enough away from center, have the dpad
                    // follow it (in floating mode)
                    // in fixed mode just clamp distance
                    val dist = Math.sqrt((xVal * xVal + yVal * yVal).toDouble())
                        .toFloat()
                    if (dPadType == "floating") {
                        if (dist > 1.5f) {
                            val sc = 1.5f / dist
                            gl.joystickCenterX = gl.joystickX - sc * (xVal / s)
                            gl.joystickCenterY = gl.joystickY - sc * (yVal / s)
                        }
                    } else if (dPadType == "fixed") {
                        if (dist > 1.01f) {
                            val sc = 1.01f / dist
                            gl.joystickX = gl.joystickCenterX + sc * (xVal / s)
                            gl.joystickY = gl.joystickCenterY + sc * (yVal / s)
                        }
                    }
                    gamePadActivity.dPadStateH = xValClamped
                    gamePadActivity.dPadStateV = yValClamped
                    gamePadActivity.doStateChange(false)
                }

                // if the touch is ending...
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                    && actionPointerIndex == i
                ) {

                    // if we hadn't moved yet, lets pass it along as a quick tap/release
                    // (useful for navigating menus)
                    if (!dPadTouchIsMove) {
                        val toRight = x - gl.joystickCenterX
                        val toLeft = gl.joystickCenterX - x
                        val toBottom = y - gl.joystickCenterY
                        val toTop = gl.joystickCenterY - y
                        // right
                        if (toRight > toLeft && toRight > toTop && toRight > toBottom) {
                            gamePadActivity.dPadStateH = 1.0f
                            gamePadActivity.dPadStateV = 0.0f
                            gamePadActivity.doStateChange(false)
                            gamePadActivity.dPadStateH = 0.0f
                            gamePadActivity.dPadStateV = 0.0f
                            gamePadActivity.doStateChange(false)
                        } else if (toLeft > toRight && toLeft > toTop && toLeft > toBottom) {
                            // left
                            gamePadActivity.dPadStateH = -1.0f
                            gamePadActivity.dPadStateV = 0.0f
                            gamePadActivity.doStateChange(false)
                            gamePadActivity.dPadStateH = 0.0f
                            gamePadActivity.dPadStateV = 0.0f
                            gamePadActivity.doStateChange(false)
                        } else if (toTop > toRight && toTop > toLeft && toTop > toBottom) {
                            gamePadActivity.dPadStateH = 0.0f
                            gamePadActivity.dPadStateV = -1.0f
                            gamePadActivity.doStateChange(false)
                            gamePadActivity.dPadStateH = 0.0f
                            gamePadActivity.dPadStateV = 0.0f
                            gamePadActivity.doStateChange(false)
                        } else {
                            gamePadActivity.dPadStateH = 0.0f
                            gamePadActivity.dPadStateV = 1.0f
                            gamePadActivity.doStateChange(false)
                            gamePadActivity.dPadStateH = 0.0f
                            gamePadActivity.dPadStateV = 0.0f
                            gamePadActivity.doStateChange(false)
                        }
                    }
                    dPadTouch = -1
                    gl.thumbPressed = false
                    gl.joystickCenterX = dPadCenterX
                    gl.joystickCenterY = dPadCenterY
                    gl.joystickX = gl.joystickCenterX
                    gl.joystickY = gl.joystickCenterY
                    gamePadActivity.dPadStateH = 0F
                    gamePadActivity.dPadStateV = 0F
                    gamePadActivity.doStateChange(false)
                }
            }
        }
        updateButtonsForTouches(event)
        requestRender()
        return true
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()

        // Create an OpenGL ES 2.0 context.
        setEGLContextClientVersion(2)

        // Set the Renderer for drawing on the GLSurfaceView
        gl = GLRenderer(gamePadActivity.applicationContext)

        // this seems to be necessary in the emulator..
        if (Build.FINGERPRINT.startsWith("generic")) {
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        }
        setRenderer(gl)

        // Render the view only when there is a change in the drawing data
        renderMode = RENDERMODE_WHEN_DIRTY
        val preferences =
            gamePadActivity.getSharedPreferences("BSRemotePrefs", Context.MODE_PRIVATE)
        dPadScale = preferences.getFloat("scale", 1.0f)
        buttonScale = preferences.getFloat("buttonScale", 1.0f)
        dPadScale = preferences.getFloat("dPadScale", 1.0f)
        dPadOffsX = preferences.getFloat("dPadOffsX", 0.4f)
        dPadOffsY = preferences.getFloat("dPadOffsY", 0.0f)
        buttonOffsX = preferences.getFloat("buttonOffsX", -0.2f)
        buttonOffsY = preferences.getFloat("buttonOffsY", 0.0f)
        dPadType = preferences.getString("dPadType", "floating")
        assert(dPadType != null)
        if (!(dPadType == "floating" || dPadType == "fixed")) {
            dPadType = "floating"
        }
        keyPickUp = preferences.getInt("keyPickUp", KeyEvent.KEYCODE_BUTTON_Y)
        keyJump = preferences.getInt("keyJump", KeyEvent.KEYCODE_BUTTON_A)
        keyPunch = preferences.getInt("keyPunch", KeyEvent.KEYCODE_BUTTON_X)
        keyBomb = preferences.getInt("keyBomb", KeyEvent.KEYCODE_BUTTON_B)
        keyRun1 = preferences.getInt("keyRun1", KeyEvent.KEYCODE_BUTTON_L1)
        keyRun2 = preferences.getInt("keyRun2", KeyEvent.KEYCODE_BUTTON_R1)
        keyStart = preferences.getInt("keyStart", KeyEvent.KEYCODE_BUTTON_START)
    }
}