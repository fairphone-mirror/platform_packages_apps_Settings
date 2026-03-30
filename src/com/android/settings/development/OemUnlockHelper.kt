/*
 * Copyright (C) 2025 Fairphone B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.fairphone.settings.development

import android.content.Context
import android.util.Log
import com.google.common.annotations.VisibleForTesting
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Interface for a listener that receives the result of an OEM unlock request.
 *
 * This allows a separate component (e.g., a UI element) to react to the outcome of the
 * asynchronous unlock request.
 */
interface OemUnlockRequestResultListener {
    /**
     * Called when the OEM unlock request has completed and a result is available.
     *
     * @param result The [OemUnlockResult] representing the outcome of the request.
     */
    fun onOemRequestResult(result: OemUnlockResult)
}

/**
 * A helper class for sending an OEM unlock request to a server.
 *
 * This class handles the asynchronous network operation using Kotlin coroutines and
 * reports the result back to a listener on the main thread.
 *
 * @param oemUnlockResultListener The listener to notify when the request is complete.
 */
class OemUnlockHelper(
    private val oemUnlockResultListener: OemUnlockRequestResultListener,
) {
    companion object {
        const val TAG = "OemUnlockHelper"
    }

    /** The coroutine scope for managing background tasks. */
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** The current job for the unlock request, which can be cancelled. */
    private var job: Job? = null

    /**
     * Initiates the process of sending an unlock request.
     *
     * This method retrieves the device's serial number and IMEI, then launches a coroutine
     * to perform the network request asynchronously. The result is then delivered to the
     * registered listener.
     *
     * @param context The [Context] used to access device information.
     */
    fun sendUnlockRequest(context: Context) {
        val serialNumber = OemUnlockUtils.getSerialNumber()
        val imei = OemUnlockUtils.getImei(context)

        job?.cancel()

        job = coroutineScope.launch {
            val result = when (val unlockResult = sendUnlockRequest(serialNumber, imei)) {
                is PostResult.Created -> OemUnlockResult.Success
                is PostResult.Forbidden -> OemUnlockResult.ForbiddenError
                is PostResult.NotFound -> OemUnlockResult.Error(unlockResult.message)
                is PostResult.ConnectionError -> OemUnlockResult.ConnectionError
                is PostResult.Error -> OemUnlockResult.Error(unlockResult.message)
            }
            withContext(Dispatchers.Main) {
                oemUnlockResultListener.onOemRequestResult(result)
            }
        }
    }

    /**
     * Performs the actual network call to send the unlock request.
     *
     * This is a suspend function that runs on a background thread and handles all network
     * communication, including creating the connection, sending the request body, and
     * parsing the response code.
     *
     * @param serialNumber The device's serial number.
     * @param imei The device's IMEI.
     * @return A [PostResult] indicating the outcome of the network call.
     */
    @VisibleForTesting
    private suspend fun sendUnlockRequest(serialNumber: String, imei: String): PostResult {
        return withContext(Dispatchers.IO) {
            val urlConnection = OemUnlockUtils.getHttpsURLConnection(
                baseUrl = OemUnlockUtils.getUnlockRequestUrl(),
                serialNumber = serialNumber,
                imei = imei,
            )
            // Add JSON body to request
            val data = JSONObject()
                .put("serialNumber", serialNumber)
                .put("imei", imei)

            try {
                OutputStreamWriter(urlConnection.outputStream).use { writer ->
                    writer.write(data.toString())
                    writer.flush()
                }

                when (urlConnection.responseCode) {
                    HttpsURLConnection.HTTP_CREATED -> PostResult.Created
                    HttpsURLConnection.HTTP_FORBIDDEN -> {
                        val errorStream = urlConnection.getErrorMessage()
                        Log.e(TAG, "HTTP_FORBIDDEN Response: $errorStream")
                        PostResult.Forbidden(errorStream)
                    }

                    HttpsURLConnection.HTTP_NOT_FOUND -> {
                        val errorStream = urlConnection.getErrorMessage()
                        Log.e(TAG, "HTTP_NOT_FOUND Response: $errorStream")
                        PostResult.NotFound(errorStream)
                    }

                    else -> {
                        val errorStream = urlConnection.getErrorMessage()
                        Log.e(
                            TAG,
                            "Error: ${urlConnection.responseCode}. Response: $errorStream",
                        )
                        PostResult.Error(
                            message = "Unexpected status code: ${urlConnection.responseCode}. " +
                                    "Response: $errorStream"
                        )
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "IOException: ${e.message}", e)
                PostResult.ConnectionError
            } catch (e: ConnectException) {
                Log.e(TAG, "ConnectException: ${e.message}", e)
                PostResult.ConnectionError
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}", e)
                PostResult.Error("Network request failed: ${e.message}")
            } finally {
                urlConnection.disconnect()
            }
        }
    }
}

/**
 * Returns the error message from the HTTP response.
 *
 * @return The error message as a String, or null if no error message is available.
 */
fun HttpURLConnection.getErrorMessage(): String =
    errorStream?.bufferedReader()?.use { it.readText() } ?: ""

/**
 * A sealed class representing the final result of an OEM unlock request.
 *
 * This provides a clean way to handle all possible outcomes in the calling code.
 */
sealed class OemUnlockResult {
    /** Represents a successful unlock request. */
    data object Success : OemUnlockResult()

    /** Represents an error with an optional message. */
    data class Error(val message: String = "") : OemUnlockResult()

    /** Represents an issue with the network connection. */
    data object ConnectionError : OemUnlockResult()

    /** Represents a server-side error. */
    data object ServerError : OemUnlockResult()

    /** Represents a forbidden request, likely due to a server policy. */
    data object ForbiddenError : OemUnlockResult()
}

/**
 * A sealed class representing the direct result of the network POST request.
 *
 * This is used internally by the helper class to map network-level outcomes to a
 * higher-level [OemUnlockResult].
 */
sealed class PostResult {
    /** Represents a successful creation response (HTTP 201). */
    data object Created : PostResult()

    /** Represents a forbidden response (HTTP 403) with a message. */
    data class Forbidden(val message: String) : PostResult()

    /** Represents a not found response (HTTP 404) with a message. */
    data class NotFound(val message: String) : PostResult()

    /** Represents a failure to connect to the server. */
    data object ConnectionError : PostResult()

    /** Represents a generic error with a message. */
    data class Error(val message: String) : PostResult()
}