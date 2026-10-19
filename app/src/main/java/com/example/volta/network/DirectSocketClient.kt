package com.example.volta.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class DirectSocketClient(private val context: Context? = null) {

    private fun bindSocketToWifi(socket: Socket) {
        if (context != null) {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val wifiNetwork = cm?.allNetworks?.firstOrNull { net ->
                    val caps = cm.getNetworkCapabilities(net)
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                }
                wifiNetwork?.bindSocket(socket)
            } catch (_: Exception) {}
        }
    }

    suspend fun sendCommand(
        host: String,
        port: Int,
        command: String,
        timeoutMs: Int = 4500
    ): Result<String> = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            bindSocketToWifi(socket)
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs

            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            val formatted = if (command.endsWith("\r\n")) command
                            else if (command.endsWith("\n")) command.replace("\n", "\r\n")
                            else "$command\r\n"
            writer.write(formatted)
            writer.flush()

            val response = StringBuilder()
            val line = reader.readLine()
            if (line != null) {
                response.append(line)
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

    suspend fun probePort(host: String, port: Int, timeoutMs: Int = 2500): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { s ->
                bindSocketToWifi(s)
                s.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun executeHardwareProvisioning(
        apIp: String,
        apPort: Int,
        rawServerIpv4: String,
        ssid: String,
        pass: String,
        timeoutMs: Int = 6000
    ): HardwareProvisioningResult = withContext(Dispatchers.IO) {
        var currentSocket: Socket? = null

        fun createConnectedSocket(): Socket {
            val s = Socket()
            bindSocketToWifi(s)
            s.connect(InetSocketAddress(apIp, apPort), timeoutMs)
            s.soTimeout = 4000
            return s
        }

        fun sendAndReceive(
            socket: Socket,
            command: String,
            expectedAck: String? = null,
            timeout: Int = 4000
        ): Pair<Boolean, String> {
            val formatted = if (command.endsWith("\r\n")) command else if (command.endsWith("\n")) command.replace("\n", "\r\n") else "$command\r\n"
            val out = socket.getOutputStream()
            val inStream = socket.getInputStream()

            // Drain any prior unread bytes before issuing the new command
            try {
                val drainBuf = ByteArray(512)
                while (inStream.available() > 0) {
                    inStream.read(drainBuf)
                }
            } catch (_: Exception) {}

            out.write(formatted.toByteArray(Charsets.US_ASCII))
            out.flush()

            val sb = StringBuilder()
            val start = System.currentTimeMillis()
            val buffer = ByteArray(512)

            while (System.currentTimeMillis() - start < timeout) {
                try {
                    val available = inStream.available()
                    if (available > 0) {
                        val count = inStream.read(buffer, 0, minOf(available, buffer.size))
                        if (count < 0) break
                        sb.append(String(buffer, 0, count, Charsets.UTF_8))
                        if (expectedAck != null && sb.contains(expectedAck, ignoreCase = true)) {
                            break
                        }
                    } else if (sb.isEmpty()) {
                        val count = inStream.read(buffer)
                        if (count < 0) break
                        sb.append(String(buffer, 0, count, Charsets.UTF_8))
                        if (expectedAck != null && sb.contains(expectedAck, ignoreCase = true)) {
                            break
                        }
                    } else {
                        if (expectedAck != null && sb.contains(expectedAck, ignoreCase = true)) {
                            break
                        }
                        Thread.sleep(30)
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    if (sb.isNotEmpty()) break
                } catch (_: Exception) {
                    break
                }
            }

            val reply = sb.toString().trim()
            val ok = if (expectedAck != null) reply.contains(expectedAck, ignoreCase = true) else reply.isNotEmpty()
            return Pair(ok, reply)
        }

        try {
            // Open primary TCP socket to strip SoftAP (192.168.1.1:30300)
            currentSocket = createConnectedSocket()

            // Step 1: Read Strip MAC & Bootinfo (Send: up:getinfo:all\r\n, fallback to up:bootinfo\r\n if needed)
            var (_, bootReply) = try {
                sendAndReceive(currentSocket, "up:getinfo:all", timeout = 2500)
            } catch (e: Exception) {
                Pair(false, "")
            }

            var macMatch = Regex("([0-9A-Fa-f]{12})").find(bootReply) ?:
                           Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})").find(bootReply)

            if (macMatch == null) {
                try {
                    val (_, biReply) = sendAndReceive(currentSocket, "up:bootinfo", timeout = 2000)
                    if (biReply.isNotBlank()) {
                        bootReply = if (bootReply.isNotBlank()) "$bootReply | $biReply" else biReply
                        macMatch = Regex("([0-9A-Fa-f]{12})").find(bootReply) ?:
                                   Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})").find(bootReply)
                    }
                } catch (_: Exception) {}
            }
            val extractedMac = macMatch?.value?.replace(":", "")?.replace("-", "")?.uppercase() ?: ""

            // Step 2: Set Backend Server IP over the SAME open socket
            // Send: up:ip:<rawServerIpv4>\r\n -> Wait for response: up:ip:ip_ok\r\n
            var (ipAccepted, ipReply) = try {
                sendAndReceive(currentSocket, "up:ip:$rawServerIpv4", expectedAck = "ip_ok", timeout = 4000)
            } catch (_: Exception) {
                Pair(false, "")
            }

            if (!ipAccepted) {
                // If not confirmed on persistent socket, try reconnecting once in case firmware closed connection
                try {
                    try { currentSocket.close() } catch (_: Exception) {}
                    Thread.sleep(250)
                    currentSocket = createConnectedSocket()
                    val retry = sendAndReceive(currentSocket, "up:ip:$rawServerIpv4", expectedAck = "ip_ok", timeout = 4000)
                    ipAccepted = retry.first
                    if (retry.second.isNotBlank()) ipReply = retry.second
                } catch (_: Exception) {}
            }

            if (!ipAccepted) {
                val detailError = if (ipReply.isBlank()) {
                    "No response from strip at $apIp:$apPort for server IP. Ensure phone is connected to the strip Wi-Fi and strip is in setup mode."
                } else {
                    "Strip did not accept server IP '$rawServerIpv4': response was '$ipReply' (expected 'up:ip:ip_ok')"
                }
                return@withContext HardwareProvisioningResult(
                    mac = extractedMac,
                    ipAccepted = false,
                    wifiAccepted = false,
                    bootReply = bootReply,
                    ipReply = ipReply,
                    connectReply = "",
                    isSuccess = false,
                    error = detailError
                )
            }

            // Step 3: Set Wi-Fi Credentials over the SAME open socket
            // Send: up:connect:<SSID>:<PASSWORD>\r\n -> Wait for response: up:connect:connect_ok\r\n
            var (wifiAccepted, connectReply) = try {
                sendAndReceive(currentSocket, "up:connect:$ssid:$pass", expectedAck = "connect_ok", timeout = 5000)
            } catch (_: Exception) {
                Pair(false, "")
            }

            if (!wifiAccepted) {
                try {
                    try { currentSocket.close() } catch (_: Exception) {}
                    Thread.sleep(250)
                    currentSocket = createConnectedSocket()
                    val retry = sendAndReceive(currentSocket, "up:connect:$ssid:$pass", expectedAck = "connect_ok", timeout = 5000)
                    wifiAccepted = retry.first
                    if (retry.second.isNotBlank()) connectReply = retry.second
                } catch (_: Exception) {}
            }

            if (!wifiAccepted) {
                val detailError = if (connectReply.isBlank()) {
                    "No response from strip for Wi-Fi credentials. Ensure the strip is powered on, within range, and in setup mode."
                } else {
                    "Strip did not accept Wi-Fi credentials: response was '$connectReply' (expected 'up:connect:connect_ok')"
                }
                return@withContext HardwareProvisioningResult(
                    mac = extractedMac,
                    ipAccepted = true,
                    wifiAccepted = false,
                    bootReply = bootReply,
                    ipReply = ipReply,
                    connectReply = connectReply,
                    isSuccess = false,
                    error = detailError
                )
            }

            // Step 4: Close Setup Socket
            try { currentSocket.close() } catch (_: Exception) {}

            // Successfully completed steps 1, 2, and 3
            HardwareProvisioningResult(
                mac = extractedMac,
                ipAccepted = true,
                wifiAccepted = true,
                bootReply = bootReply,
                ipReply = ipReply,
                connectReply = connectReply,
                isSuccess = true
            )
        } catch (e: Exception) {
            HardwareProvisioningResult(
                mac = "",
                ipAccepted = false,
                wifiAccepted = false,
                bootReply = "",
                ipReply = "",
                connectReply = "",
                isSuccess = false,
                error = "Hardware socket communication failure on $apIp:$apPort: ${e.message}"
            )
        } finally {
            try { currentSocket?.close() } catch (_: Exception) {}
        }
    }
}

data class HardwareProvisioningResult(
    val mac: String,
    val ipAccepted: Boolean,
    val wifiAccepted: Boolean,
    val bootReply: String,
    val ipReply: String,
    val connectReply: String,
    val isSuccess: Boolean,
    val error: String? = null
)

