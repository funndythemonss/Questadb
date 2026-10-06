package com.example.questadbbridge

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var log: TextView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        log = findViewById(R.id.log)

        try { installAdb() } catch (_: Exception) { append("ADB executable is not installed.") }

        findViewById<Button>(R.id.pair).setOnClickListener {
            val ip = findViewById<EditText>(R.id.ip).text.toString().trim()
            val port = findViewById<EditText>(R.id.pairPort).text.toString().trim()
            val code = findViewById<EditText>(R.id.code).text.toString().trim()
            if (ip.isBlank() || port.isBlank() || !Regex("^\\d{6}$").matches(code)) {
                status.text = "ENTER IP, PAIRING PORT, AND 6-DIGIT CODE"
                return@setOnClickListener
            }
            scope.launch { pairAndConnect(ip, port, code) }
        }
    }

    private fun installAdb() {
        val adb = File(filesDir, "adb")
        if (!adb.exists()) {
            assets.open("adb").use { input ->
                FileOutputStream(adb).use { output -> input.copyTo(output) }
            }
        }
        if (!adb.setExecutable(true, false)) throw IllegalStateException("Could not mark ADB executable.")
    }

    private suspend fun pairAndConnect(ip: String, port: String, code: String) = withContext(Dispatchers.IO) {
        try {
            setStatus("PAIRING…")
            val paired = runAdb(listOf("pair", "$ip:$port", code))
            append(paired)
            if (!paired.contains("Successfully paired", true)) { setStatus("PAIRING FAILED"); return@withContext }

            setStatus("PAIRED — DISCOVERING CONNECT PORT…")
            val services = runAdb(listOf("mdns", "services"))
            append(services)
            val target = services.lineSequence().map { it.trim() }.firstNotNullOfOrNull { line ->
                if ("_adb-tls-connect._tcp" !in line) null
                else line.split(Regex("\\s+")).lastOrNull()?.takeIf { it.contains(":") }
            }
            if (target == null) {
                setStatus("PAIRED — CONNECT PORT NOT FOUND")
                append("ADB pairing succeeded, but mDNS did not return a connect endpoint.")
                return@withContext
            }

            setStatus("CONNECTING…")
            val connected = runAdb(listOf("connect", target))
            append(connected)
            if (connected.contains("connected to", true)) setStatus("🟢 YOU'RE CONNECTED")
            else setStatus("🔴 CONNECTION FAILED")
        } catch (e: Exception) {
            append(e.message ?: e.toString())
            setStatus("🔴 BRIDGE ERROR")
        }
    }

    private fun runAdb(args: List<String>): String {
        val adb = File(filesDir, "adb")
        if (!adb.exists()) installAdb()
        val process = ProcessBuilder(listOf(adb.absolutePath) + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        return output
    }

    private fun setStatus(value: String) = runOnUiThread { status.text = value }
    private fun append(value: String) = runOnUiThread { log.append("\n$value") }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
