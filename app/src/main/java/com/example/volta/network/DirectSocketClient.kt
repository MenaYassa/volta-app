package com.example.volta.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class DirectSocketClient {

    suspend fun sendCommand(
        host: String,
        port: Int,
        command: String,
        timeoutMs: Int = 4000
    ): Result<String> = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs

            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            val formatted = if (command.endsWith("\n")) command else "$command\r\n"
            writer.write(formatted)
            writer.flush()

            val response = StringBuilder()
            val charBuffer = CharArray(1024)
            val read = reader.read(charBuffer)
            if (read > 0) {
                response.append(charBuffer, 0, read)
            }
            Result.success(response.toString().trim())
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    suspend fun probePort(host: String, port: Int, timeoutMs: Int = 2000): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
