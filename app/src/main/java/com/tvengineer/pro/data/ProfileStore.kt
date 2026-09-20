package com.tvengineer.pro.data

import android.content.Context
import com.tvengineer.pro.model.CommandRisk
import com.tvengineer.pro.model.DeviceProfile
import com.tvengineer.pro.model.RemoteCommand
import com.tvengineer.pro.model.TransportType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ProfileStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("tv_engineer_pro", Context.MODE_PRIVATE)

    fun load(): MutableList<DeviceProfile> {
        val raw = prefs.getString("profiles", null)
        if (raw.isNullOrBlank()) return builtIns().toMutableList()
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i -> decodeProfile(arr.getJSONObject(i)) }
        }.getOrElse { builtIns().toMutableList() }
    }

    fun save(items: List<DeviceProfile>) {
        val arr = JSONArray()
        items.forEach { arr.put(encodeProfile(it)) }
        prefs.edit().putString("profiles", arr.toString()).apply()
    }

    fun exportProfile(profile: DeviceProfile): String = encodeProfile(profile).toString(2)

    fun importProfile(json: String): DeviceProfile = decodeProfile(JSONObject(json))

    fun blankProfile(name: String, brand: String, model: String, type: TransportType) =
        DeviceProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            brand = brand,
            model = model,
            transport = type
        )

    private fun encodeCommand(c: RemoteCommand) = JSONObject()
        .put("key", c.key).put("label", c.label).put("payload", c.payload).put("risk", c.risk.name)

    private fun decodeCommand(o: JSONObject) = RemoteCommand(
        key = o.getString("key"),
        label = o.optString("label", o.getString("key")),
        payload = o.optString("payload", ""),
        risk = runCatching { CommandRisk.valueOf(o.optString("risk", "NORMAL")) }.getOrDefault(CommandRisk.NORMAL)
    )

    private fun encodeMap(map: Map<String, RemoteCommand>): JSONArray {
        val arr = JSONArray()
        map.values.forEach { arr.put(encodeCommand(it)) }
        return arr
    }

    private fun decodeMap(arr: JSONArray?): Map<String, RemoteCommand> {
        if (arr == null) return emptyMap()
        val out = linkedMapOf<String, RemoteCommand>()
        for (i in 0 until arr.length()) {
            val c = decodeCommand(arr.getJSONObject(i))
            out[c.key] = c
        }
        return out
    }

    private fun encodeProfile(p: DeviceProfile) = JSONObject()
        .put("id", p.id).put("name", p.name).put("brand", p.brand).put("model", p.model)
        .put("transport", p.transport.name).put("host", p.host).put("port", p.port)
        .put("token", p.token).put("clientKey", p.clientKey).put("psk", p.psk)
        .put("normal", encodeMap(p.normal)).put("service", encodeMap(p.service))

    private fun decodeProfile(o: JSONObject) = DeviceProfile(
        id = o.optString("id", UUID.randomUUID().toString()),
        name = o.optString("name", "TV"),
        brand = o.optString("brand", "Generic"),
        model = o.optString("model", "Unknown"),
        transport = runCatching { TransportType.valueOf(o.optString("transport", "IR")) }.getOrDefault(TransportType.IR),
        host = o.optString("host", ""),
        port = o.optInt("port", 0),
        token = o.optString("token", ""),
        clientKey = o.optString("clientKey", ""),
        psk = o.optString("psk", ""),
        normal = decodeMap(o.optJSONArray("normal")),
        service = decodeMap(o.optJSONArray("service"))
    )

    private fun rc(key: String, payload: String, label: String = key, risk: CommandRisk = CommandRisk.NORMAL) =
        RemoteCommand(key, label, payload, risk)

    private fun builtIns(): List<DeviceProfile> {
        val samsungKeys = mapOf(
            "POWER" to rc("POWER","KEY_POWER"),
            "HOME" to rc("HOME","KEY_HOME"), "INPUT" to rc("INPUT","KEY_SOURCE"),
            "SETTINGS" to rc("SETTINGS","KEY_MENU"), "UP" to rc("UP","KEY_UP"),
            "DOWN" to rc("DOWN","KEY_DOWN"), "LEFT" to rc("LEFT","KEY_LEFT"),
            "RIGHT" to rc("RIGHT","KEY_RIGHT"), "OK" to rc("OK","KEY_ENTER"),
            "BACK" to rc("BACK","KEY_RETURN"), "EXIT" to rc("EXIT","KEY_EXIT"),
            "VOL_UP" to rc("VOL_UP","KEY_VOLUP"), "VOL_DOWN" to rc("VOL_DOWN","KEY_VOLDOWN"),
            "CH_UP" to rc("CH_UP","KEY_CHUP"), "CH_DOWN" to rc("CH_DOWN","KEY_CHDOWN"),
            "MUTE" to rc("MUTE","KEY_MUTE"), "INFO" to rc("INFO","KEY_INFO"),
            "GUIDE" to rc("GUIDE","KEY_GUIDE"), "PLAY" to rc("PLAY","KEY_PLAY"),
            "PAUSE" to rc("PAUSE","KEY_PAUSE"), "STOP" to rc("STOP","KEY_STOP")
        )

        val rokuKeys = listOf(
            "Home","Back","Up","Down","Left","Right","Select","Info","Rev","Fwd","Play",
            "VolumeUp","VolumeDown","VolumeMute","Power","ChannelUp","ChannelDown"
        ).associate { k -> k.uppercase() to rc(k.uppercase(), k) }

        val lgKeys = mapOf(
            "POWER" to rc("POWER","ssap://system/turnOff"),
            "VOL_UP" to rc("VOL_UP","ssap://audio/volumeUp"),
            "VOL_DOWN" to rc("VOL_DOWN","ssap://audio/volumeDown"),
            "MUTE" to rc("MUTE","ssap://audio/setMute?mute=true"),
            "HOME" to rc("HOME","BUTTON:HOME"), "BACK" to rc("BACK","BUTTON:BACK"),
            "UP" to rc("UP","BUTTON:UP"), "DOWN" to rc("DOWN","BUTTON:DOWN"),
            "LEFT" to rc("LEFT","BUTTON:LEFT"), "RIGHT" to rc("RIGHT","BUTTON:RIGHT"),
            "OK" to rc("OK","BUTTON:ENTER"), "INPUT" to rc("INPUT","BUTTON:INPUT"),
            "SETTINGS" to rc("SETTINGS","BUTTON:MENU")
        )

        val serviceShell = listOf("SSM","IN_START","SOUND","ADJUST","PWR_ONLY","IN_STOP","PSM")
            .associateWith { rc(it, "", it.replace("_"," "), CommandRisk.ENGINEER) }

        return listOf(
            DeviceProfile("generic-ir","Generic IR Lab","Generic","Import RAW / Pronto / NEC",TransportType.IR,
                normal = emptyMap(), service = serviceShell),
            DeviceProfile("lg-webos","LG webOS","LG","webOS network",TransportType.LG_WEBOS, port=3000,
                normal = lgKeys, service = serviceShell),
            DeviceProfile("samsung-tizen","Samsung Tizen","Samsung","Tizen network",TransportType.SAMSUNG_TIZEN, port=8001,
                normal = samsungKeys, service = serviceShell),
            DeviceProfile("roku","Roku TV / Player","Roku","ECP network",TransportType.ROKU_ECP, port=8060,
                normal = rokuKeys, service = serviceShell),
            DeviceProfile("philips","Philips JointSPACE","Philips","JointSPACE / Android TV",TransportType.PHILIPS_JOINTSPACE, port=1925,
                normal = mapOf(
                    "POWER" to rc("POWER","Standby"), "HOME" to rc("HOME","Home"),
                    "UP" to rc("UP","CursorUp"), "DOWN" to rc("DOWN","CursorDown"),
                    "LEFT" to rc("LEFT","CursorLeft"), "RIGHT" to rc("RIGHT","CursorRight"),
                    "OK" to rc("OK","Confirm"), "BACK" to rc("BACK","Back"),
                    "VOL_UP" to rc("VOL_UP","VolumeUp"), "VOL_DOWN" to rc("VOL_DOWN","VolumeDown"),
                    "MUTE" to rc("MUTE","Mute")
                ), service = serviceShell),
            DeviceProfile("sony","Sony BRAVIA IRCC","Sony","Network IRCC",TransportType.SONY_IRCC,
                normal = emptyMap(), service = serviceShell)
        )
    }
}
