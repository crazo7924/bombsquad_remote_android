package net.froemling.bsremote

import java.net.InetAddress

class ServerEntry {
    var address: InetAddress = InetAddress.getLocalHost()
    var port = 0
    var lastPingTime: Long = 0
}