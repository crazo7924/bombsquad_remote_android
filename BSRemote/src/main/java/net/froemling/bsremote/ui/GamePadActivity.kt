package net.froemling.bsremote.ui

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.SystemClock
import android.provider.Settings.Secure
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import net.froemling.bsremote.LogThread
import net.froemling.bsremote.R
import net.froemling.bsremote.WorkerThread
import net.froemling.bsremote.ui.gl.MainGLSurfaceView
import java.io.IOException
import java.net.*
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.experimental.and
import kotlin.math.abs
import kotlin.math.roundToInt

open class GamePadActivity : Activity() {

    private var mWindowIsFocused = false

    @JvmField
    var buttonStateV1: Int = 0

    @JvmField
    var buttonStateV2: Int = 0

    @JvmField
    var dPadStateH = 0.0f

    @JvmField
    var dPadStateV = 0.0f
    private var shutDownStartTime: Long = 0
    private var averageLag = 0f
    private lateinit var lagMeter: TextView
    private var uniqueID = 0
    private lateinit var statesV1: IntArray
    private lateinit var statesV2: IntArray
    private var currentLag = 0f
    private var readThread: WorkerThread? = null

    // (bits 6-10 are d-pad h-value and bits 11-15 are dpad v-value)
    private lateinit var processThread: WorkerThread
    private lateinit var processTimer: Timer
    private lateinit var processUITimer: Timer
    private lateinit var shutDownTimer: Timer
    private lateinit var socket: DatagramSocket
    private lateinit var addr // actual address we're talking to
            : InetAddress
    private lateinit var addrs // for initial scanning
            : Array<InetAddress>
    private lateinit var addrsValid: BooleanArray
    private var port = 0
    private var requestID = 0
    private var id: Byte = 0
    private var dead = false
    private var lastLagUpdateTime: Long = 0
    private var nextState = 0
    private var connected = false
    private var shouldPrintConnected = false
    private var requestedState = 0
    private var shuttingDown = false
    private var lastNullStateTime: Long = 0
    private lateinit var stateBirthTimes: LongArray
    private lateinit var stateLastSentTimes: LongArray
    private var lastSentState: Long = 0
    private var usingProtocolV2 = false
    private var mGLView: MainGLSurfaceView? = null
    private var addrsRaw: Array<String>? = null
    private var newStyle = false
    override fun onCreate(savedInstanceState: Bundle?) {

        // keep android from crashing due to our network use in the main thread
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        // keep the device awake while this activity is visible.
        // granted most of the time the user is tapping on it, but if they have
        // a hardware game-pad attached they might not be.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // clients use a random request ID to differentiate themselves
        // (probably is a better way to do this..)
        val currentTime = SystemClock.uptimeMillis()
        requestID = currentTime.toInt() % 10000
        id = -1 // ain't got one yet
        stateBirthTimes = LongArray(256)
        stateLastSentTimes = LongArray(256)
        statesV1 = IntArray(256)
        statesV2 = IntArray(256)

        // if we reconnect we may get acks for states we didn't send..
        // so lets set everything to current time to avoid screwing up
        // our lag-meter
        val curTime = SystemClock.uptimeMillis()
        for (i in 0..255) {
            stateBirthTimes[i] = curTime
            stateLastSentTimes[i] = 0
        }
        val extras = intent.extras
        if (extras != null) {
            newStyle = extras.getBoolean("newStyle")
            port = extras.getInt("connectPort")
            try {
                socket = DatagramSocket()
            } catch (e: SocketException) {
                LogThread.log("Error setting up gamepad socket", e, this)
            }
            addrsRaw = extras.getStringArray("connectAddrs")
        }

        // read or create our random unique ID; we tack this onto our
        // android device identifier just in case its not actually unique
        // (as apparently was the case with nexus-2 or some other device)
        val preferences = getSharedPreferences("BSRemotePrefs", MODE_PRIVATE)
        uniqueID = preferences.getInt("uniqueId", 0)
        if (uniqueID == 0) {
            while (uniqueID == 0) {
                uniqueID = Random().nextInt() and 0xFFFF
            }
            val editor = preferences.edit()
            editor.putInt("uniqueId", uniqueID)
            editor.apply()
        }
        processThread = WorkerThread()
        processThread.start()
        readThread = WorkerThread()
        readThread!!.start()

        // all the read-thread does is wait for data to come in
        // and pass it to the process-thread
        readThread!!.doRunnable {
            while (true) {
                try {
                    val buf = ByteArray(10)
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    abstract class PacketRunnable(val p: DatagramPacket) : Runnable {
                        abstract override fun run()
                    }
                    processThread.doRunnable(
                        object : PacketRunnable(packet) {
                            override fun run() {
                                // (delay for testing disconnect race conditions)
                                //                try {
                                //                  Thread.sleep(1000);
                                //                } catch (InterruptedException e) {
                                //                  e.printStackTrace();
                                //                }
                                readFromSocket(p)
                            }
                        })
                } catch (e: IOException) {
                    // assuming this means the socket is closed..
                    if (debug) {
                        Log.v(TAG, "READ THREAD DYING")
                    }
                    readThread!!.looper.quitSafely()
                    readThread = null
                    break
                } catch (e: ArrayIndexOutOfBoundsException) {
                    LogThread.log("Got excessively sized datagram packet", e, this@GamePadActivity)
                }
            }
        }
        super.onCreate(savedInstanceState)

        // Create a GLSurfaceView instance and set it
        // as the ContentView for this Activity
        mGLView = MainGLSurfaceView(this)
        val mLayout: ViewGroup = RelativeLayout(this)
        mLayout.addView(mGLView)
        lagMeter = TextView(this)
        lagMeter.setTextColor(-0xff0100)
        lagMeter.text = "--"
        val params = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
        params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
        params.bottomMargin = 20
        mLayout.addView(lagMeter, params)
        setContentView(mLayout)
    }

    fun shutDown() {
        if (debug) {
            Log.v(TAG, "SETTING shuttingDown")
        }
        shuttingDown = true
        shutDownStartTime = SystemClock.uptimeMillis()

        // Create our shutdown timer.. this will keep us
        // trying to disconnect cleanly with the server until
        // we get confirmation or we give up.
        // Tell our worker thread to start its update timer.
        processThread.doRunnable {
            if (debug) {
                Log.v(TAG, "CREATING SHUTDOWN TIMER...")
            }
            shutDownTimer = Timer()
            shutDownTimer.schedule(
                object : TimerTask() {
                    override fun run() {
                        // when this timer fires, tell our process thread to run
                        processThread.doRunnable { process() }
                    }
                },
                0,
                100
            )
        }

        // let our gl view clean up anything it needs to
        if (mGLView != null) {
            mGLView!!.onClosing()
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (debug) {
            Log.v(TAG, "onDestroy()")
        }
        shutDown()
    }

    override fun onStart() {
        super.onStart()
        if (debug) {
            Log.v(TAG, "GPA onStart()")
        }

        // tell our worker thread to start its update timer
        processThread.doRunnable {

            // kick off an id request... (could just wait for the process
            // timer to do this)
            if (id.toInt() == -1) {
                sendIdRequest()
            }
            if (debug) {
                Log.v(TAG, "CREATING PROCESS TIMER..")
            }
            processTimer = Timer()
            processTimer.schedule(
                object : TimerTask() {
                    override fun run() {
                        processThread.doRunnable { process() }
                    }
                },
                0,
                100
            )

            // lets also do some upkeep stuff at a slower pace..
            processUITimer = Timer()
            processUITimer.schedule(
                object : TimerTask() {
                    override fun run() {
                        // when this timer fires, tell our process thread to run
                        runOnUiThread { processUI() }
                    }
                },
                0,
                2500
            )
        }
    }

    override fun onStop() {
        super.onStop()
        processThread.doRunnable {
            processTimer.cancel()
            processTimer.purge()
            processUITimer.cancel()
            processUITimer.purge()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return super.onKeyDown(keyCode, event)
    }

    // handle periodic processing such as receipt re-requests
    protected fun processUI() {
        // eww as of android 4.4.2 there's several things that cause
        // immersive mode state to get lost.. (such as volume up/down buttons)
        // ...so for now lets force the issue
        if (mWindowIsFocused) {
            setImmersiveMode()
        }
    }

    @TargetApi(19)
    private fun setImmersiveMode() {
        val vis = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        if (mGLView!!.systemUiVisibility != vis) {
            mGLView!!.systemUiVisibility = vis
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        mWindowIsFocused = hasFocus
        if (hasFocus) {
            setImmersiveMode()
        }
    }

    fun sendDisconnectPacket() {
        if (id.toInt() != -1) {
            val data = ByteArray(2)
            data[0] = REMOTE_MSG_DISCONNECT
            data[1] = id
            try {
                socket.send(DatagramPacket(data, data.size, addr, port))
            } catch (e: IOException) {
                LogThread.log("", e, this@GamePadActivity)
            }
        }
    }

    private fun process() {
        if (Thread.currentThread() !== processThread) {
            Log.v(TAG, "Incorrect thread on process")
            return
            // throw new AssertionError("thread error");
        }

        // if any of these happen after we're dead totally ignore them
        if (dead) {
            return
        }
        val t = SystemClock.uptimeMillis()

        // if we're shutting down but are still connected, keep sending
        // disconnects out
        if (shuttingDown) {
            var finishUsOff = false

            // shoot off another disconnect notice
            // once we're officially disconnected we can die
            if (!connected) {
                finishUsOff = true
            } else {
                sendDisconnectPacket()
                // just give up after a short while
                if (t - shutDownStartTime > 5000) {
                    finishUsOff = true
                }
            }
            if (finishUsOff && !dead) {
                dead = true
                // ok we're officially dead.. clear out our threads/timers/etc
                shutDownTimer.cancel()
                shutDownTimer.purge()
                processThread.looper.quitSafely()
                // this should kill our read-thread
                socket.close()
                return
            }
        }

        // if we've got states we haven't heard an ack for yet, keep shipping 'em
        // out
        val stateDiff = requestedState - nextState and 0xFF

        // if they've requested a state we don't have yet, we don't need to
        // resend anything
        if (stateDiff < 128) {
            // .. however we wanna shoot states at the server every now and then
            // even if we have no new states,
            // to keep from timing out and such..
            if (t - lastNullStateTime > 3000) {
                doStateChange(true)
                lastNullStateTime = t
            }
        } else {
            // ok we've got at least one state we haven't heard confirmation for
            // yet.. lets ship 'em out..
            if (usingProtocolV2) {
                shipUnAckedStatesV2()
            } else {
                shipUnAckedStatesV1()
            }
        }

        // if we don't have an ID yet, keep sending off those requests...
        if (id.toInt() == -1) {
            sendIdRequest()
        }

        // update our lag meter every so often
        if (t - lastLagUpdateTime > 2000) {
            val smoothing = 0.5f
            averageLag = smoothing * averageLag + (1.0f - smoothing) * currentLag

            // lets show half of our the round-trip time as lag.. (the actual
            // delay
            // in-game is just our packets getting to them; not the round trip)
            runOnUiThread {
                when {
                    shouldPrintConnected -> {
                        lagMeter.setTextColor(-0xff0100)
                        lagMeter.setText(R.string.connected)
                        shouldPrintConnected = false
                    }
                    connected -> {
                        // convert from millisecs to seconds
                        val averageLagMs = averageLag * 0.5f / 1000.0f
                        when {
                            averageLagMs < 0.1 -> {
                                lagMeter.setTextColor(-0x770078)
                            }
                            averageLagMs < 0.2 -> {
                                lagMeter.setTextColor(-0x4c9a)
                            }
                            else -> {
                                lagMeter.setTextColor(-0x999a)
                            }
                        }
                        lagMeter.text =
                            String.format(getString(R.string.lag).replace("\${SECONDS}", "%.2f"),
                                averageLagMs)
                    }
                    else -> {
                        // connecting...
                        lagMeter.setTextColor(-0x7800)
                        lagMeter.setText(R.string.connecting)
                    }
                }
            }
            currentLag = 0.0f
            lastLagUpdateTime = t
        }
    }

    private fun shipUnAckedStatesV1() {

        // if we don't have an id yet this is moot..
        if (id.toInt() == -1) {
            return
        }
        val curTime = SystemClock.uptimeMillis()

        // ok we need to ship out everything from their last requested state
        // to our current state.. (clamping at a reasonable value)
        if (id.toInt() != -1) {
            var statesToSend = nextState - requestedState and 0xFF
            if (statesToSend > 11) {
                statesToSend = 11
            }
            if (statesToSend < 1) {
                return
            }
            val data = ByteArray(100)
            data[0] = REMOTE_MSG_STATE
            data[1] = id
            data[2] = statesToSend.toByte() // number of states we have here
            var s = nextState - statesToSend and 0xFF
            if (debug) {
                Log.v(TAG, "SENDING $statesToSend STATES FROM: $s")
            }
            data[3] = (s and 0xFF).toByte() // starting index

            // pack em in
            var index = 4
            for (i in 0 until statesToSend) {
                data[index++] = ((statesV1[s] and 0xff).toByte())
                data[index++] = ((statesV1[s] shr 8).toByte())
                stateLastSentTimes[s] = curTime
                s = (s + 1) % 256
            }
            if (connected) {
                try {
                    socket.send(DatagramPacket(data, 4 + 2 * statesToSend, addr, port))
                } catch (e: IOException) {

                    // if anything went wrong here, assume the game just shut down
                    runOnUiThread {
                        val msg = getString(R.string.gameShutDown)
                        val context = applicationContext
                        val duration = Toast.LENGTH_LONG
                        val toast = Toast.makeText(context, msg, duration)
                        toast.show()
                    }
                    connected = false
                    finish()
                }
            }
        }
    }

    private fun shipUnAckedStatesV2() {

        // if we don't have an id yet this is moot..
        if (id.toInt() == -1) {
            return
        }
        val curTime = SystemClock.uptimeMillis()

        // ok we need to ship out everything from their last requested state
        // to our current state.. (clamping at a reasonable value)
        if (id.toInt() != -1) {
            var statesToSend = nextState - requestedState and 0xFF
            if (statesToSend > 11) {
                statesToSend = 11
            }
            if (statesToSend < 1) {
                return
            }
            val data = ByteArray(150)
            data[0] = REMOTE_MSG_STATE2.toByte()
            data[1] = id
            data[2] = statesToSend.toByte() // number of states we have here
            var s = nextState - statesToSend and 0xFF
            if (debug) {
                Log.v(TAG, "SENDING $statesToSend STATES FROM: $s")
            }
            data[3] = (s and 0xFF).toByte() // starting index

            // pack em in
            var index = 4
            for (i in 0 until statesToSend) {
                data[index++] = (statesV2[s] and 0xFF).toByte()
                data[index++] = (statesV2[s] shr 8).toByte()
                data[index++] = (statesV2[s] shr 16).toByte()
                stateLastSentTimes[s] = curTime
                s = (s + 1) % 256
            }
            if (connected) {
                try {
                    socket.send(DatagramPacket(data, 4 + 3 * statesToSend, addr, port))
                } catch (e: IOException) {

                    // if anything went wrong here, assume the game just shut down
                    runOnUiThread {
                        val msg = getString(R.string.gameShutDown)
                        val context = applicationContext
                        val duration = Toast.LENGTH_LONG
                        val toast = Toast.makeText(context, msg, duration)
                        toast.show()
                    }
                    connected = false
                    finish()
                }
            }
        }
    }

    fun doStateChange(force: Boolean) {
        if (usingProtocolV2) {
            doStateChangeV2(force)
        } else {
            doStateChangeV1(force)
        }
    }

    private fun doStateChangeV2(force: Boolean) {

        // compile our state value
        var s = buttonStateV2 // buttons
        var hVal = (256.0f * (0.5f + dPadStateH * 0.5f)).toInt()
        if (hVal < 0) {
            hVal = 0
        } else if (hVal > 255) {
            hVal = 255
        }
        var vVal = (256.0f * (0.5f + dPadStateV * 0.5f)).toInt()
        if (vVal < 0) {
            vVal = 0
        } else if (vVal > 255) {
            vVal = 255
        }
        s = s or (hVal shl 8)
        s = s or (vVal shl 16)

        // if our compiled state value hasn't changed, don't send.
        // (analog joystick noise can send a bunch of redundant states through
        // here)
        // The exception is if forced is true, which is the case with packets
        // that double as keep-alive s.
        if (s.toLong() == lastSentState && !force) {
            return
        }
        stateBirthTimes[nextState] = SystemClock.uptimeMillis()
        stateLastSentTimes[nextState] = 0
        if (debug) {
            Log.v(TAG, "STORING NEXT STATE: $nextState")
        }
        statesV2[nextState] = s
        nextState = (nextState + 1) % 256
        lastSentState = s.toLong()

        // if we're pretty up to date as far as state acks, lets go ahead
        // and send out this state immediately..
        // (keeps us nice and responsive on low latency networks)
        val unackedCount = nextState - requestedState and 0xFF // upcast to
        // get unsigned
        if (unackedCount < 3) {
            shipUnAckedStatesV2()
        }
    }

    private fun doStateChangeV1(force: Boolean) {

        // compile our state value
        var s = buttonStateV1 // buttons
        s = s or ((if (dPadStateH > 0) 1 else 0) shl 5) // sign bit
        s = s or ((1.0.coerceAtMost(abs(dPadStateH).toDouble()) * 15.0).roundToInt() shl 6) // mag
        s = s or ((if (dPadStateV > 0) 1 else 0) shl 10) // sign bit
        s = s or ((1.0.coerceAtMost(abs(dPadStateV).toDouble()) * 15.0).roundToInt() shl 11) // mag

        // if our compiled state value hasn't changed, don't send.
        // (analog joystick noise can send a bunch of redundant states through here)
        // The exception is if forced is true, which is the case with packets that
        // double as keep-alive s.
        if (s.toLong() == lastSentState && !force) {
            return
        }
        stateBirthTimes[nextState] = SystemClock.uptimeMillis()
        stateLastSentTimes[nextState] = 0
        if (debug) {
            Log.v(TAG, "STORING NEXT STATE: $nextState")
        }
        statesV1[nextState] = s
        nextState = (nextState + 1) % 256
        lastSentState = s.toLong()

        // if we're pretty up to date as far as state acks, lets go ahead
        // and send out this state immediately..
        // (keeps us nice and responsive on low latency networks)
        val unackedCount = nextState - requestedState and 0xFF // upcast to
        // get
        // unsigned
        if (unackedCount < 3) {
            shipUnAckedStatesV1()
        }
    }

    private fun sendIdRequest() {
        if (connected) {
            throw AssertionError()
        }
        if (id.toInt() != -1) {
            throw AssertionError()
        }
        if (Thread.currentThread() !== processThread) {
            throw AssertionError()
        }

        // get our unique identifier to tack onto the end of our name
        // (so if we drop our connection and reconnect we can be reunited with
        // our old
        // dude instead of leaving a zombie)
        @SuppressLint("HardwareIds") val androidId =
            Secure.getString(applicationContext.contentResolver, Secure.ANDROID_ID)

        // on new-style connections we include unique id info in our name so we
        // can be re-connected
        // if we disconnect
        val deviceName: String
        if (newStyle) {
            var name = ScanActivity.playerName
            // make sure we don't have any #s in it
            name = name.replace("#".toRegex(), "")
            deviceName = "$name#$androidId$uniqueID"
        } else {
            deviceName = "Android remote (" + Build.MODEL + ")"
        }
        val nameBytes: ByteArray = deviceName.toByteArray(charsetUTF8)
        var dLen = nameBytes.size
        if (dLen > 99) {
            dLen = 99
        }

        // send a hello on all our addrs
        // Log.v(TAG, "Sending ID Request");
        val data = ByteArray(128)
        data[0] = REMOTE_MSG_ID_REQUEST
        data[1] = 121 // old protocol version..
        data[2] = (requestID and 0xFF).toByte()
        data[3] = (requestID shr 8).toByte()
        data[4] = 50 // protocol version request (this implies we support
        // state-packet-2 if they do)
        var len = 5
        for (i in 0 until dLen) {
            data[5 + i] = nameBytes[i]
            len++
        }
        // resolve our addresses if we haven't yet (couldn't do this in main thread)
        run {
            var haveValidAddress = false
            var i = 0
            while (i < addrs.size) {
                if (!addrsValid[i]) {
                    i++
                    continue
                }
                haveValidAddress = true
                val addr = addrs[i]
                val packet = DatagramPacket(data, len, addr, port)
                try {
                    socket.send(packet)
                } catch (e: IOException) {
                    LogThread.log("Error on ID-request send", e, this@GamePadActivity)
                    Log.e(TAG, "Error on ID-request send: " + e.message)
                    e.printStackTrace()
                } catch (e: NullPointerException) {
                    LogThread.log("Error on ID-request send", e, this@GamePadActivity)
                    Log.e(TAG, "Bad IP specified: " + e.message)
                }
                i++
                i++
            }
            // if no addresses were valid, lets just quit out..
            if (!haveValidAddress) {
                finish()
            }
        }
    }

    private fun readFromSocket(packet: DatagramPacket) {
        val buffer = packet.data
        val amt = packet.length
        if (Thread.currentThread() !== processThread) {
            Log.v(TAG, "_readFromSocket called in unexpected thread; ignoring.")
            return
            // throw new AssertionError();
        }
        if (amt > 0) {
            if (buffer[0] == REMOTE_MSG_ID_RESPONSE) {
                if (debug) {
                    Log.v(TAG, "Got ID response")
                }
                if (amt == 3) {
                    if (connected) {
                        if (debug) {
                            Log.v(TAG, "Already connected; ignoring ID response")
                        }
                        return
                    }
                    // for whatever reason .connect started behaving wonky in android 7
                    // or so
                    // ..(perhaps ipv6 related?..)
                    // ..looks like just grabbing the addr and feeding it to our outgoing
                    // datagrams works though
                    addr = packet.address
                    // hooray we have an id.. we're now officially connected
                    id = buffer[1]

                    // we said we support protocol v2.. if they respond with 100, they
                    // do too.
                    usingProtocolV2 = buffer[2] == 100.toByte()
                    nextState = 0 // start over with this ID
                    connected = true
                    shouldPrintConnected = true
                    if (id.toInt() == -1) {
                        throw AssertionError()
                    }
                } else {
                    Log.e(TAG, "INVALID ID RESPONSE!")
                }
            } else if (buffer[0] == REMOTE_MSG_STATE_ACK) {
                if (amt == 2) {
                    val time = SystemClock.uptimeMillis()
                    // take note of the next state they want...
                    // move ours up to that point (if we haven't yet)
                    if (debug) {
                        Log.v(TAG, "GOT STATE ACK TO " + (buffer[1] and 0xFF.toByte()))
                    }
                    val stateDiff = buffer[1] - requestedState and 0xFF // upcast to
                    // positive
                    if (stateDiff in 1..127) {
                        requestedState = (requestedState + (stateDiff - 1)) % 256
                        val lag = time - stateBirthTimes[requestedState]
                        if (lag > currentLag) {
                            currentLag = lag.toFloat()
                        }
                        requestedState = (requestedState + 1) % 256
                        if (requestedState != (buffer[1] and 0xFF.toByte()).toInt()) {
                            throw AssertionError()
                        }
                    }
                }
            } else if (buffer[0] == REMOTE_MSG_DISCONNECT_ACK) {
                if (debug) {
                    Log.v(TAG, "GOT DISCONNECT ACK!")
                }
                if (amt == 1) {
                    connected = false
                    finish()
                }
            } else if (buffer[0] == REMOTE_MSG_DISCONNECT) {
                if (amt == 2) {
                    // ignore disconnect msgs for the first second or two in case we're
                    // doing a quick disconnect/reconnect and some old ones come
                    // trickling in
                    run {
                        val msg: String = when {
                            buffer[1] == BS_REMOTE_ERROR_VERSION_MISMATCH -> {
                                getString(R.string.versionMismatch)
                            }
                            buffer[1] == BS_REMOTE_ERROR_GAME_SHUTTING_DOWN -> {
                                getString(R.string.gameShutDown)
                            }
                            buffer[1] == BS_REMOTE_ERROR_NOT_ACCEPTING_CONNECTIONS -> {
                                getString(R.string.gameFull)
                            }
                            else -> {
                                getString(R.string.disconnected)
                            }
                        }

                        class ToastRunnable(private val s: String) : Runnable {
                            override fun run() {
                                val context = applicationContext
                                val duration = Toast.LENGTH_LONG
                                val toast = Toast.makeText(context, s, duration)
                                toast.show()
                            }
                        }
                        runOnUiThread(ToastRunnable(msg))
                        connected = false
                        finish()
                    }
                }
            }
        }
    }

    companion object {
        // flip this on for lots of log spewage to help diagnose oddities
        const val debug = false
        const val TAG = "BSRemoteGamePad"
        const val BS_REMOTE_STATE_PUNCH = 1
        const val BS_REMOTE_STATE_JUMP = 1 shl 1
        const val BS_REMOTE_STATE_THROW = 1 shl 2
        const val BS_REMOTE_STATE_BOMB = 1 shl 3
        const val BS_REMOTE_STATE_MENU = 1 shl 4
        const val BS_REMOTE_STATE2_MENU = 1
        const val BS_REMOTE_STATE2_JUMP = 1 shl 1
        const val BS_REMOTE_STATE2_PUNCH = 1 shl 2
        const val BS_REMOTE_STATE2_THROW = 1 shl 3
        const val BS_REMOTE_STATE2_BOMB = 1 shl 4
        const val BS_REMOTE_STATE2_RUN = 1 shl 5
        const val BS_REMOTE_ERROR_VERSION_MISMATCH: Byte = 0
        const val BS_REMOTE_ERROR_GAME_SHUTTING_DOWN: Byte = 1
        const val BS_REMOTE_ERROR_NOT_ACCEPTING_CONNECTIONS: Byte = 2
        const val REMOTE_MSG_ID_REQUEST: Byte = 2
        const val REMOTE_MSG_ID_RESPONSE: Byte = 3
        const val REMOTE_MSG_DISCONNECT: Byte = 4
        const val REMOTE_MSG_STATE: Byte = 5
        const val REMOTE_MSG_STATE_ACK: Byte = 6
        const val REMOTE_MSG_DISCONNECT_ACK: Byte = 7
        const val REMOTE_MSG_STATE2 = 10
        val charsetUTF8: Charset
            get() = StandardCharsets.UTF_8
    }
}