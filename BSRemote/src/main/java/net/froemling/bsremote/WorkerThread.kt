package net.froemling.bsremote

import android.os.Handler
import android.os.HandlerThread
import android.os.Message

class WorkerThread : HandlerThread("Worker"), Handler.Callback {
    private var mHandler: Handler? = null
    fun doRunnable(runnable: Runnable) {
        mHandler = Handler(looper, this)
        val msg = mHandler?.obtainMessage(0, runnable)
        if (msg != null) {
            mHandler?.sendMessage(msg)
        }
    }

    override fun handleMessage(msg: Message): Boolean {
        val runnable = msg.obj as Runnable
        runnable.run()
        return true
    }
}