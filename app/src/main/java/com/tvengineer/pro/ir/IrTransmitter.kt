package com.tvengineer.pro.ir

import android.content.Context
import android.hardware.ConsumerIrManager

class IrTransmitter(context: Context) {
    private val manager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    fun available(): Boolean = manager?.hasIrEmitter() == true

    fun ranges(): String {
        if (!available()) return "No Consumer IR emitter"
        return manager?.carrierFrequencies?.joinToString(" • ") { "${it.minFrequency/1000}-${it.maxFrequency/1000} kHz" }
            ?: "IR available"
    }

    fun send(spec: String) {
        check(available()) { "This phone does not expose an Android Consumer IR emitter." }
        val signal = IrEncoder.parse(spec)
        validate(signal)
        manager!!.transmit(signal.carrierHz, signal.pattern)
    }

    private fun validate(signal: IrEncoder.Signal) {
        require(signal.carrierHz in 20_000..100_000) { "Carrier outside 20-100 kHz" }
        require(signal.pattern.size >= 2) { "IR pattern is empty" }
        require(signal.pattern.all { it > 0 }) { "IR durations must be positive" }
        require(signal.pattern.sumOf { it.toLong() } < 2_000_000L) { "Android limits one IR pattern to under 2 seconds" }
    }
}
