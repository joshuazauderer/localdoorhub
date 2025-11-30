package com.localdoorhub.app.data

import android.util.Log
import com.localdoorhub.app.data.models.DoorbellEvent
import com.localdoorhub.app.data.models.DoorbellStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Converts URLs from production format (192.168.8.1) to emulator format (10.0.2.2:3002)
 * This is needed because Android emulator uses 10.0.2.2 to access the host machine
 * Also ensures port 3002 is included for media URLs
 * External URLs (not starting with 192.168.8.1) are returned unchanged
 */
fun convertUrlForEmulator(url: String?): String? {
    if (url == null) return null
    
    // Don't convert external URLs (like Google Cloud Storage, etc.)
    if (!url.contains("192.168.8.1")) {
        return url
    }
    
    // Convert 192.168.8.1 to 10.0.2.2:3002 (with port for API server)
    var converted = url
        .replace("http://192.168.8.1/", "http://10.0.2.2:3002/")
        .replace("https://192.168.8.1/", "https://10.0.2.2:3002/")
        .replace("http://192.168.8.1:", "http://10.0.2.2:")
        .replace("https://192.168.8.1:", "https://10.0.2.2:")
    
    // If URL still doesn't have a port and is using 10.0.2.2, add port 3002
    if (converted.startsWith("http://10.0.2.2/") && !converted.contains(":3002")) {
        converted = converted.replace("http://10.0.2.2/", "http://10.0.2.2:3002/")
    }
    if (converted.startsWith("https://10.0.2.2/") && !converted.contains(":3002")) {
        converted = converted.replace("https://10.0.2.2/", "https://10.0.2.2:3002/")
    }
    
    return converted
}

class DoorbellRepository(
    // For Android Emulator, use: "http://10.0.2.2:3002"
    // For physical device on same network, use: "http://YOUR_COMPUTER_IP:3002"
    private val baseUrl: String = "http://10.0.2.2:3002"
) {
    private val TAG = "DoorbellRepository"

    suspend fun getStatus(): DoorbellStatus = withContext(Dispatchers.IO) {
        try {
            Log.d("DoorbellRepository", "Fetching status from: $baseUrl/api/v1/status")
            val json = getJson("$baseUrl/api/v1/status")
            val status = DoorbellStatus(
                doorbellOnline = json.optBoolean("doorbellOnline", false),
                doorName = json.optString("doorName", null).takeIf { it.isNotEmpty() },
                batteryPercent = json.optInt("batteryPercent").takeIf { it > 0 },
                wifiStrength = json.optString("wifiStrength", null).takeIf { it.isNotEmpty() },
                rtspUrl = json.optString("rtspUrl", null).takeIf { it.isNotEmpty() }
            )
            Log.d("DoorbellRepository", "Status received: doorbellOnline=${status.doorbellOnline}")
            status
        } catch (e: Exception) {
            Log.e("DoorbellRepository", "Error fetching status: ${e.message}", e)
            DoorbellStatus(
                doorbellOnline = false,
                doorName = null,
                batteryPercent = null,
                wifiStrength = null,
                rtspUrl = null
            )
        }
    }

    suspend fun getEvents(): List<DoorbellEvent> = withContext(Dispatchers.IO) {
        try {
            val arr = getJsonArray("$baseUrl/api/v1/events")
            (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                DoorbellEvent(
                    id = obj.getString("id"),
                    type = obj.getString("type"),
                    timestamp = obj.getString("timestamp"),
                    thumbnailUrl = obj.optString("thumbnailUrl", null).takeIf { it.isNotEmpty() }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun testChime(): Boolean = withContext(Dispatchers.IO) {
        try {
            postSimple("$baseUrl/api/v1/test-chime")
        } catch (e: Exception) {
            false
        }
    }

    suspend fun testStream(): Boolean = withContext(Dispatchers.IO) {
        try {
            postSimple("$baseUrl/api/v1/test-stream")
        } catch (e: Exception) {
            false
        }
    }

    suspend fun finishSetup(doorName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            postSimple("$baseUrl/api/v1/finish-setup", """{"doorName":"$doorName"}""")
        } catch (e: Exception) {
            false
        }
    }

    suspend fun hasActiveCall(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking active call status from: $baseUrl/api/v1/active-call")
            val json = getJson("$baseUrl/api/v1/active-call")
            val active = json.optBoolean("active", false)
            Log.d(TAG, "Active call status: $active")
            active
        } catch (e: Exception) {
            Log.e(TAG, "Error checking active call: ${e.message}", e)
            false
        }
    }

    suspend fun startCall(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting call")
            postSimple("$baseUrl/api/v1/start-call")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting call: ${e.message}", e)
            false
        }
    }

    suspend fun endCall(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Ending call")
            postSimple("$baseUrl/api/v1/end-call")
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call: ${e.message}", e)
            false
        }
    }

    suspend fun sendAudio(audioFile: java.io.File): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Sending audio file: ${audioFile.name}, size: ${audioFile.length()} bytes")
            val conn = URL("$baseUrl/api/v1/talk").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            
            val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            
            conn.outputStream.use { outputStream ->
                // Write boundary
                outputStream.write("--$boundary\r\n".toByteArray())
                outputStream.write("Content-Disposition: form-data; name=\"audio\"; filename=\"audio.m4a\"\r\n".toByteArray())
                outputStream.write("Content-Type: audio/mp4\r\n\r\n".toByteArray())
                
                // Write file content
                audioFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                
                // Write closing boundary
                outputStream.write("\r\n--$boundary--\r\n".toByteArray())
            }
            
            val code = conn.responseCode
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "Audio send response: $code - $response")
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio: ${e.message}", e)
            false
        }
    }

    private fun getJson(urlStr: String): JSONObject {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        conn.connect()
        
        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val errorBody = try {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error body"
            } catch (e: Exception) {
                "Error reading error stream: ${e.message}"
            }
            conn.disconnect()
            throw java.io.IOException("HTTP $responseCode: $errorBody")
        }
        
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return JSONObject(body)
    }

    private fun getJsonArray(urlStr: String): JSONArray {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        conn.connect()
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return JSONArray(body)
    }

    private fun postSimple(urlStr: String, payload: String? = null): Boolean {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = payload != null
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        if (payload != null) {
            conn.outputStream.use { os ->
                os.write(payload.toByteArray())
            }
        }
        val code = conn.responseCode
        conn.disconnect()
        return code in 200..299
    }
}

