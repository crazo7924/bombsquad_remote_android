package net.froemling.bsremote.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import net.froemling.bsremote.*
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*

open class ScanActivity : Activity() {
    private lateinit var listView: ListView
    protected lateinit var adapter: LibraryAdapter
    lateinit var serverEntries: MutableMap<String, ServerEntry>
    private lateinit var processTimer: Timer
    private lateinit var scannerThread: WorkerThread
    private lateinit var stopperThread: WorkerThread
    private lateinit var readThread: WorkerThread
    private lateinit var scannerSocket: DatagramSocket
    private var lastGameClickTime: Long = 0L
    public override fun onCreate(savedInstanceState: Bundle?) {
        if (debug) {
            Log.v(TAG, "onCreate()")
        }
        super.onCreate(savedInstanceState)

        // we want to use our longer title here but we cant set it in the
        // manifest since it carries over to our icon; grumble.
        setTitle(R.string.app_name)
        setContentView(R.layout.gen_list)
        scannerThread = WorkerThread()
        scannerThread.start()
        stopperThread = WorkerThread()
        stopperThread.start()
        adapter = LibraryAdapter(this, this)
        listView = findViewById(android.R.id.list)
        listView.addHeaderView(adapter.headerView, null, false)
        listView.adapter = adapter

        // pull our player name from prefs
        val preferences = getSharedPreferences("BSRemotePrefs", MODE_PRIVATE)
        val playerNameVal = preferences.getString("playerName", "")!!
        if (playerNameVal != "") {
            playerName = playerNameVal
        }
        val editText = adapter.headerView.findViewById<EditText>(R.id.nameEditText)
        if (editText != null) {

            // hmm this counts emoji as 2 still but whatever...
            editText.filters = arrayOf<InputFilter>(LengthFilter(10))
            editText.setText(playerName)
            editText.addTextChangedListener(
                object : TextWatcher {
                    override fun onTextChanged(
                        s: CharSequence,
                        start: Int,
                        before: Int,
                        count: Int,
                    ) {
                        playerName = s.toString()
                        val editor = preferences.edit()
                        editor.putString("playerName", playerName)
                        editor.apply()
                    }

                    override fun beforeTextChanged(
                        s: CharSequence,
                        start: Int,
                        count: Int,
                        after: Int,
                    ) {
                    }

                    override fun afterTextChanged(s: Editable) {}
                })
        }
        listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, view: View, _: Int, _: Long ->
                val name = (view.findViewById<View>(android.R.id.text1) as TextView).text.toString()
                scannerThread.doRunnable(
                    object : ObjRunnable(name) {
                        override fun run() {

                            // prevent case where double tapping an entry quickly enough can
                            // bring up two game-pad activities
                            val currentTime = SystemClock.uptimeMillis()
                            if (currentTime - lastGameClickTime < 2000) {
                                Log.v(TAG, "Suppressing repeat join-game tap.")
                                return
                            }
                            lastGameClickTime = currentTime
                            if (serverEntries.containsKey(obj)) {
                                val se = serverEntries[obj] ?: return
                                val gamePadIntent =
                                    Intent(this@ScanActivity, GamePadActivity::class.java)
                                gamePadIntent.putExtra("connectAddrs",
                                    arrayOf(se.address.hostName))
                                gamePadIntent.putExtra("connectPort", se.port)
                                gamePadIntent.putExtra("newStyle", true)
                                startActivity(gamePadIntent)
                            }
                        }
                    })
            }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.act_scan, menu)
        return true
    }

    public override fun onDestroy() {
        if (debug) {
            Log.v(TAG, "onDestroy()")
        }
        super.onDestroy()
        // have the worker threads shut themselves down
        scannerThread.doRunnable {
            scannerThread.looper.quitSafely()

            // have the worker threads shut themselves down
            stopperThread.doRunnable {
                stopperThread.looper.quitSafely()
            }
        }
    }

    public override fun onPause() {
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
    }

    public override fun onStart() {
        if (debug) {
            Log.v(TAG, "onStart()")
        }
        super.onStart()
        adapter.knownGames.clear()
        adapter.notifyDataSetChanged()
        serverEntries = HashMap()
        try {
            scannerSocket = DatagramSocket()
            scannerSocket.broadcast = true
        } catch (e1: SocketException) {
            LogThread.log("Error setting up scanner socket", e1, this)
        }
        readThread = WorkerThread()
        readThread.start()

        // all the read-thread does is wait for data to come in
        // and pass it to the process-thread
        readThread.doRunnable {
            while (true) {
                try {
                    val buf = ByteArray(256)
                    val packet = DatagramPacket(buf, buf.size)
                    scannerSocket.receive(packet)
                    abstract class PacketRunnable(val p: DatagramPacket) : Runnable {
                        abstract override fun run()
                    }
                    scannerThread.doRunnable(
                        object : PacketRunnable(packet) {
                            override fun run() {

                                // if this is a game response packet...
                                if (p.data.size > 1
                                    && p.data[0] == BS_PACKET_REMOTE_GAME_RESPONSE
                                ) {
                                    // extract name
                                    val s = String(Arrays.copyOfRange(p.data, 1, p.length))

                                    // if this one isn't on our list, add it and
                                    // inform the ui of its existence
                                    if (!serverEntries.containsKey(s)) {
                                        serverEntries[s] = ServerEntry()
                                        // hmm should we store its address only
                                        // when adding or every time
                                        // we hear from them?..
                                        val entry = serverEntries[s]!!
                                        entry.address = p.address
                                        entry.port = p.port
                                        runOnUiThread(
                                            object : ObjRunnable(s) {
                                                override fun run() {
                                                    adapter.notifyFound(obj)
                                                }
                                            })
                                    }
                                    val entry = serverEntries[s]!!
                                    entry.lastPingTime = SystemClock.uptimeMillis()
                                }
                            }
                        })
                } catch (e: IOException) {
                    // assuming this means the socket is closed..
                    readThread.looper.quitSafely()
                    break
                } catch (e: ArrayIndexOutOfBoundsException) {
                    LogThread.log("Got excessively sized datagram packet", e, this@ScanActivity)
                }
            }
        }

        // start our timer to send out query packets, etc
        processTimer = Timer()
        processTimer.schedule(
            object : TimerTask() {
                override fun run() {

                    // when this timer fires, tell our process thread to run
                    scannerThread.doRunnable {


                        // send broadcast packets to all our network
                        // interfaces to find games
                        try {
                            val sendData = ByteArray(1)
                            sendData[0] = BS_PACKET_REMOTE_GAME_QUERY.toByte()
                            val interfaces = NetworkInterface.getNetworkInterfaces()
                            while (interfaces.hasMoreElements()) {
                                val networkInterface = interfaces.nextElement()
                                if (!networkInterface.isUp || networkInterface.isLoopback) {
                                    continue
                                }
                                for (interfaceAddress in networkInterface.interfaceAddresses) {
                                    val broadcast = interfaceAddress.broadcast ?: continue
                                    try {
                                        val sendPacket = DatagramPacket(sendData,
                                            sendData.size,
                                            broadcast,
                                            43210)
                                        scannerSocket.send(sendPacket)
                                    } catch (e: Exception) {
                                        Log.v(TAG, "Broadcast datagram send " + "error")
                                    }
                                }
                            }
                        } catch (ex: IOException) {
                            LogThread.log("", ex, this@ScanActivity)
                        }

                        // prune servers we haven't heard from in a while..
                        val curTime = SystemClock.uptimeMillis()
                        val it: MutableIterator<Map.Entry<String, ServerEntry>> =
                            serverEntries.entries.iterator()
                        while (it.hasNext()) {
                            val thisEntry = it.next()
                            val age = curTime - thisEntry.value.lastPingTime
                            if (age > 5000) {
                                runOnUiThread(
                                    object : ObjRunnable(thisEntry.key) {
                                        override fun run() {
                                            adapter.notifyLost(obj)
                                        }
                                    })
                                it.remove()
                            }
                        }
                    }
                }
            },
            0,
            1000)
    }

    public override fun onStop() {
        if (debug) {
            Log.v(TAG, "onStop()")
        }
        super.onStop()
        processTimer.cancel()
        processTimer.purge()
        scannerSocket.close()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_search_by_ip) {

            // Creating the AlertDialog object
            val ipDialog = AlertDialog.Builder(this)
            val layoutInflater =
                applicationContext.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            @SuppressLint("InflateParams") val view = layoutInflater.inflate(R.layout.dialog, null)
            val preferences = getSharedPreferences("BSRemotePrefs", MODE_PRIVATE)
            val addrVal = preferences.getString("manualConnectAddress", "")

            // set up EditText to get input
            val editText = view.findViewById<EditText>(R.id.editText)
            editText.setText(addrVal)
            editText.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI

            // handle OK clicks
            ipDialog.setPositiveButton(
                R.string.connect
            ) { _: DialogInterface?, _: Int ->
                val ipFromUser = editText.text.toString().trim { it <= ' ' }
                val editor = preferences.edit()
                editor.putString("manualConnectAddress", ipFromUser)
                editor.apply()
                val myIntent = Intent(this@ScanActivity, GamePadActivity::class.java)
                myIntent.putExtra("connectAddrs", arrayOf(ipFromUser))
                myIntent.putExtra("connectPort", 43210)
                myIntent.putExtra("newStyle", true)
                startActivity(myIntent)
            }

            // handle cancel clicks
            ipDialog.setNegativeButton(
                R.string.cancel
            ) { _: DialogInterface?, _: Int -> }
            ipDialog.setView(view)
            ipDialog.show()
            return true
        }
        return false
    }

    companion object {
        const val debug = false
        const val TAG = "BSRemoteScan"
        const val BS_PACKET_REMOTE_GAME_QUERY = 8
        const val BS_PACKET_REMOTE_GAME_RESPONSE: Byte = 9
        var playerName: String = "The Dude"
    }
}