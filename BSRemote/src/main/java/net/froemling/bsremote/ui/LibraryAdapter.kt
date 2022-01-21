package net.froemling.bsremote.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import net.froemling.bsremote.LogThread
import net.froemling.bsremote.R
import java.util.*

class LibraryAdapter constructor(
    private val scanActivity: ScanActivity,
    var context: Context,
) : BaseAdapter() {
    @kotlin.jvm.JvmField
    val knownGames = LinkedList<String>()
    var inflater: LayoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    @SuppressLint("InflateParams")
    @JvmField
    val headerView: View = inflater.inflate(R.layout.item_network, null, false)
    fun notifyLost(gameName: String) {
        if (Looper.getMainLooper().thread !== Thread.currentThread()) {
            throw AssertionError()
        }
        if (knownGames.contains(gameName)) {
            knownGames.remove(gameName)
            notifyDataSetChanged()
        }
    }

    fun notifyFound(gameName: String) {
        if (Looper.getMainLooper().thread !== Thread.currentThread()) {
            throw AssertionError()
        }
        if (!knownGames.contains(gameName)) {
            knownGames.add(gameName)
            notifyDataSetChanged()
        }
    }

    override fun getItem(position: Int): String {
        return if (!knownGames.isEmpty()) {
            knownGames[position]
        } else {
            ""
        }
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    override fun getCount(): Int {
        return knownGames.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    @SuppressLint("SetTextI18n")
    override fun getView(position: Int, convertView: View, parent: ViewGroup): View {

        val tv = convertView.findViewById<TextView>(android.R.id.text1)
        if (getItem(position) == "") {
            LogThread.log("Problem getting zero-conf info", null, scanActivity)
            tv.text = "unknown"
            return convertView
        }
        tv.text = getItem(position)
        return convertView
    }

}