package com.tvengineer.pro.model

enum class TransportType { IR, LG_WEBOS, SAMSUNG_TIZEN, ROKU_ECP, PHILIPS_JOINTSPACE, SONY_IRCC, GENERIC_HTTP }
enum class CommandRisk { NORMAL, ENGINEER, DANGEROUS }

data class RemoteCommand(
    val key: String,
    val label: String = key,
    val payload: String,
    val risk: CommandRisk = CommandRisk.NORMAL
)

data class DeviceProfile(
    val id: String,
    val name: String,
    val brand: String,
    val model: String,
    val transport: TransportType,
    val host: String = "",
    val port: Int = 0,
    val token: String = "",
    val clientKey: String = "",
    val psk: String = "",
    val normal: Map<String, RemoteCommand> = emptyMap(),
    val service: Map<String, RemoteCommand> = emptyMap()
)

data class DiscoveryResult(
    val name: String,
    val host: String,
    val location: String,
    val server: String,
    val hint: String
)

data class DiagnosticItem(
    val title: String,
    val value: String,
    val ok: Boolean
)

data class LogEntry(
    val time: Long = System.currentTimeMillis(),
    val level: String = "INFO",
    val message: String
)
