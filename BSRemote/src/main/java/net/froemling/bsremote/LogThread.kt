package net.froemling.bsremote

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.util.Pair
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*

class LogThread private constructor(c: Context?, private val log: String) : Thread("Error") {
    private var version: String? = null

    @Throws(UnsupportedEncodingException::class)
    private fun getQuery(params: List<Pair<String, String?>>): String {
        val result = StringBuilder()
        var first = true
        for (pair in params) {
            if (first) {
                first = false
            } else {
                result.append("&")
            }
            result.append(URLEncoder.encode(pair.first, "UTF-8"))
            result.append("=")
            result.append(URLEncoder.encode(pair.second, "UTF-8"))
        }
        return result.toString()
    }

    override fun run() {
        try {
            val url = URL("http://acrobattleserver.appspot" + ".com" + "/bsRemoteLog")
            val conn = url.openConnection() as HttpURLConnection
            conn.allowUserInteraction = false
            conn.requestMethod = "POST"
            val userAgent = ("BombSquad Remote "
                    + version
                    + " (Android "
                    + Build.VERSION.RELEASE
                    + "; "
                    + Build.MANUFACTURER
                    + " "
                    + Build.MODEL
                    + ")")
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("charset", "utf-8")
            val nameValuePairs: MutableList<Pair<String, String?>> = ArrayList(2)
            nameValuePairs.add(Pair("version", version))
            nameValuePairs.add(Pair("log", log))
            val out = getQuery(nameValuePairs)
            val postDataBytes = out.toByteArray(charsetUTF8)
            val os = conn.outputStream
            os.write(postDataBytes)
            os.flush()
            os.close()
            val responseCode = conn.responseCode
            if (responseCode != 200) {
                Log.v("BSREMOTE", "Got response code $responseCode on bsRemoteLog request")
            }
        } catch (e: IOException) {
            Log.v("BSREMOTE", "ERR ON LogThread post")
        }
    }

    companion object {
        private var sent = false

        @JvmStatic
        fun log(s: String, e: Throwable?, c: Context?) {
            // only report the first error..
            var s = s
            if (!sent) {
                if (e != null) {
                    val sw = StringWriter()
                    e.printStackTrace(PrintWriter(sw))
                    if (s != "") {
                        s += "\n"
                    }
                    s += sw.toString()
                }
                LogThread(c, s).start()
                sent = true
            }
            // report all to the standard log
            Log.e("BSREMOTE", s)
            e?.printStackTrace()
        }

        private val charsetUTF8: Charset
            get() = StandardCharsets.UTF_8
    }

    init {
        try {
            version = if (c == null) {
                "?"
            } else {
                c.packageManager.getPackageInfo(c.packageName, 0).versionName
            }
        } catch (ignored: PackageManager.NameNotFoundException) {
        }
    }
}