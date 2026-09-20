package com.tvengineer.pro.ir

import kotlin.math.roundToInt

object IrEncoder {
    data class Signal(val carrierHz: Int, val pattern: IntArray)

    fun parse(spec: String): Signal {
        val s = spec.trim()
        val upper = s.uppercase()
        return when {
            upper.startsWith("RAW:") -> {
                val parts = s.split(":", limit = 3)
                val carrier = parts.getOrNull(1)?.toIntOrNull() ?: 38000
                val pattern = parts.getOrNull(2).orEmpty()
                    .replace("[","").replace("]","")
                    .split(',', ' ', ';').filter { it.isNotBlank() }.map { it.trim().toInt() }.toIntArray()
                Signal(carrier, pattern)
            }
            upper.startsWith("PRONTO:") -> pronto(s.substringAfter(':'))
            upper.startsWith("NEC:") -> {
                val p = s.split(":")
                nec(parseNum(p.getOrElse(1){"0"}).toInt(), parseNum(p.getOrElse(2){"0"}).toInt())
            }
            upper.startsWith("SAMSUNG32:") -> samsung32(parseNum(s.substringAfter(':')))
            upper.startsWith("SIRC12:") -> {
                val p=s.split(":"); sonySirc(parseNum(p.getOrElse(1){"0"}).toInt(), parseNum(p.getOrElse(2){"0"}).toInt(),12)
            }
            upper.startsWith("SIRC15:") -> {
                val p=s.split(":"); sonySirc(parseNum(p.getOrElse(1){"0"}).toInt(), parseNum(p.getOrElse(2){"0"}).toInt(),15)
            }
            upper.startsWith("SIRC20:") -> {
                val p=s.split(":"); sonySirc(parseNum(p.getOrElse(1){"0"}).toInt(), parseNum(p.getOrElse(2){"0"}).toInt(),20)
            }
            else -> throw IllegalArgumentException("Unknown IR format. Use RAW, PRONTO, NEC, SAMSUNG32 or SIRC.")
        }
    }

    fun nec(address: Int, command: Int): Signal {
        val a = address and 0xff
        val c = command and 0xff
        val frame = a.toLong() or (((a xor 0xff).toLong()) shl 8) or
            (c.toLong() shl 16) or (((c xor 0xff).toLong()) shl 24)
        val out = mutableListOf(9000,4500)
        repeat(32) { bit ->
            out += 560
            out += if (((frame shr bit) and 1L) == 1L) 1690 else 560
        }
        out += 560
        return Signal(38000, out.toIntArray())
    }

    fun samsung32(data: Long): Signal {
        val out = mutableListOf(4500,4500)
        repeat(32) { idx ->
            val bit = 31-idx
            out += 560
            out += if (((data shr bit) and 1L) == 1L) 1690 else 560
        }
        out += 560
        return Signal(38000, out.toIntArray())
    }

    fun sonySirc(device: Int, command: Int, bits: Int): Signal {
        require(bits in listOf(12,15,20))
        var data = (command and 0x7f).toLong() or ((device.toLong()) shl 7)
        val out = mutableListOf(2400,600)
        repeat(bits) { bit ->
            out += if (((data shr bit) and 1L) == 1L) 1200 else 600
            out += 600
        }
        return Signal(40000, out.toIntArray())
    }

    fun pronto(text: String): Signal {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map { it.toInt(16) }
        require(words.size >= 6 && words[0] == 0x0000) { "Only learned Pronto 0000 is supported" }
        val carrier = (1_000_000.0 / (words[1] * 0.241246)).roundToInt()
        val periodUs = 1_000_000.0 / carrier
        val pairs = ((words.size - 4) / 2).coerceAtMost(if (words[2] > 0) words[2] else words[3])
        val pattern = IntArray(pairs*2) { i -> (words[4+i] * periodUs).roundToInt().coerceAtLeast(1) }
        return Signal(carrier, pattern)
    }

    private fun parseNum(v:String):Long {
        val s=v.trim()
        return if(s.startsWith("0x",true)) s.substring(2).toLong(16) else s.toLong()
    }
}
