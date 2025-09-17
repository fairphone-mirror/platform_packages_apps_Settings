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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import com.android.internal.telephony.PhoneConstants
import java.net.URL
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

const val FACTORY_API_BASE_URL = "https://gateway.service.fairphone.com/factory/api"
const val API_ENDPOINT_UNLOCK_REQUEST = "unlock-requests"
const val DEVICE_ID_HEADER_NAME = "X-DEVICE-ID"
const val TIMEOUT = 30 * 1000 // 30s

object OemUnlockUtils {
    /**
     * Retrieves the IMEI of the device from the first SIM slot.
     *
     * @param context The [Context] used to access system services.
     * @return The IMEI as a [String], or a potentially empty string if the permission is missing or the IMEI is unavailable.
     */
    @SuppressLint("MissingPermission")
    fun getImei(context: Context): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return telephonyManager.getImei(PhoneConstants.SIM_ID_1)
    }

    /**
     * Retrieves the device's hardware serial number.
     *
     * @return The serial number as a [String].
     */
    @SuppressLint("MissingPermission")
    fun getSerialNumber(): String {
        return Build.getSerial()
    }

    /**
     * Creates and configures an [HttpsURLConnection] for making a POST request.
     *
     * The connection is set up with specific headers, including a custom device ID header.
     *
     * @param baseUrl The base URL for the connection.
     * @param serialNumber The device's serial number.
     * @param imei The device's IMEI.
     * @return A pre-configured [HttpsURLConnection] object.
     */
    fun getHttpsURLConnection(baseUrl: String, serialNumber: String, imei: String): HttpsURLConnection {
        val url = URL(baseUrl)
        val urlConnection = url.openConnection() as HttpsURLConnection
        urlConnection.apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty(DEVICE_ID_HEADER_NAME, getDeviceIdHeader(serialNumber, imei))

        }
        return urlConnection
    }

    /**
     * Constructs the full URL for the unlock request API endpoint.
     *
     * @return The complete URL for the unlock request as a [String].
     */
    fun getUnlockRequestUrl(): String {
        return "$FACTORY_API_BASE_URL/$API_ENDPOINT_UNLOCK_REQUEST"
    }

    /**
     * Generates a Base64-encoded device ID header string, needed for the API gateway.
     *
     * The header is created by combining the serial number and IMEI, separated by a colon,
     * and then encoding the result in Base64.
     *
     * @param serialNumber The device's serial number.
     * @param imei The device's IMEI.
     * @return A Base64-encoded [String] representing the device ID header.
     */
    fun getDeviceIdHeader(serialNumber: String, imei: String): String {
        val deviceId = "$serialNumber:$imei"
        return Base64.getEncoder().encodeToString(deviceId.encodeToByteArray())
    }


    /**
     * Checks if the current Android OS build is a debug build (userdebug or eng).
     *
     * @return `true` if the build type is "userdebug" or "eng", `false` otherwise.
     */
    fun isDebugOsBuild(): Boolean {
        return Build.TYPE != "user"
    }
}
