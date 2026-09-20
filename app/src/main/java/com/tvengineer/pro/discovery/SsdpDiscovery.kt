package com.tvengineer.pro.discovery

import android.content.Context
import android.net.wifi.WifiManager
import com.tvengineer.pro.model.DiscoveryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI

class SsdpDiscovery(private val context:Context) {
    suspend fun scan(timeoutMs:Int=2200):List<DiscoveryResult> = withContext(Dispatchers.IO) {
        val wifi=context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock=wifi?.createMulticastLock("tvengineer-ssdp")?.apply{setReferenceCounted(false);acquire()}
        val out=linkedMapOf<String,DiscoveryResult>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout=350
                val msg=("M-SEARCH * HTTP/1.1\r\n"+
                        "HOST: 239.255.255.250:1900\r\n"+
                        "MAN: \"ssdp:discover\"\r\n"+
                        "MX: 2\r\n"+
                        "ST: ssdp:all\r\n\r\n").toByteArray()
                socket.send(DatagramPacket(msg,msg.size,InetAddress.getByName("239.255.255.250"),1900))
                val start=System.currentTimeMillis()
                while(System.currentTimeMillis()-start<timeoutMs) {
                    try {
                        val buf=ByteArray(8192)
                        val p=DatagramPacket(buf,buf.size)
                        socket.receive(p)
                        val text=String(p.data,0,p.length)
                        val headers=text.lines().mapNotNull {
                            val i=it.indexOf(':')
                            if(i>0) it.substring(0,i).trim().lowercase() to it.substring(i+1).trim() else null
                        }.toMap()
                        val loc=headers["location"].orEmpty()
                        val host=runCatching { URI(loc).host }.getOrNull() ?: p.address.hostAddress.orEmpty()
                        val server=headers["server"].orEmpty()
                        val st=headers["st"].orEmpty()
                        val usn=headers["usn"].orEmpty()
                        val hint=listOf(server,st,usn).joinToString(" ").lowercase().let {
                            when {
                                "roku" in it -> "Roku"
                                "webos" in it || "lge" in it -> "LG webOS"
                                "samsung" in it -> "Samsung"
                                "philips" in it -> "Philips"
                                "sony" in it -> "Sony"
                                else -> "UPnP / Smart TV"
                            }
                        }
                        if(host.isNotBlank()) out[host]=DiscoveryResult(hint,host,loc,server,hint)
                    } catch(_:Exception) {}
                }
            }
        } finally { runCatching { lock?.release() } }
        out.values.toList()
    }
}
