package com.tvengineer.pro.controller

import android.content.Context
import com.tvengineer.pro.ir.IrTransmitter
import com.tvengineer.pro.model.DeviceProfile
import com.tvengineer.pro.model.TransportType
import com.tvengineer.pro.network.*

class TvController(private val context:Context) {
    private val ir=IrTransmitter(context)

    fun irStatus()=ir.ranges()
    fun hasIr()=ir.available()

    suspend fun send(profile:DeviceProfile,payload:String):Result<String> {
        return when(profile.transport) {
            TransportType.IR -> runCatching { ir.send(payload); "IR transmitted" }
            TransportType.ROKU_ECP -> RokuClient(profile.host, if(profile.port>0) profile.port else 8060).send(payload)
            TransportType.SAMSUNG_TIZEN -> SamsungTizenClient(profile.host, if(profile.port>0) profile.port else 8001, profile.token).send(payload)
            TransportType.LG_WEBOS -> LgWebOsClient(profile.host, if(profile.port>0) profile.port else 3000, profile.clientKey).send(payload)
            TransportType.PHILIPS_JOINTSPACE -> PhilipsJointSpaceClient(profile.host, if(profile.port>0) profile.port else 1925).send(payload)
            TransportType.SONY_IRCC -> SonyIrccClient(profile.host,profile.psk).send(payload)
            TransportType.GENERIC_HTTP -> GenericHttpClient(profile.host).send(payload)
        }
    }

    suspend fun probe(profile:DeviceProfile):Result<String> {
        return when(profile.transport) {
            TransportType.IR -> if(ir.available()) Result.success(ir.ranges()) else Result.failure(IllegalStateException("No IR emitter"))
            TransportType.ROKU_ECP -> RokuClient(profile.host, if(profile.port>0)profile.port else 8060).probe()
            TransportType.SAMSUNG_TIZEN -> SamsungTizenClient(profile.host, if(profile.port>0)profile.port else 8001, profile.token).probe()
            TransportType.LG_WEBOS -> LgWebOsClient(profile.host, if(profile.port>0)profile.port else 3000, profile.clientKey).probe()
            TransportType.PHILIPS_JOINTSPACE -> PhilipsJointSpaceClient(profile.host, if(profile.port>0)profile.port else 1925).probe()
            TransportType.SONY_IRCC -> SonyIrccClient(profile.host,profile.psk).probe()
            TransportType.GENERIC_HTTP -> GenericHttpClient(profile.host).probe()
        }
    }
}
