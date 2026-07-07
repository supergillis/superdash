package com.superdash.esphome

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.superdash.core.log.Log

private val log = Log("EsphomeMdns")

/** Advertises the ESPHome native API service via mDNS. TXT records mirror
 *  what esphome.io/components/mdns documents:
 *  - `version`: ESPHome firmware version we claim.
 *  - `mac`: MAC-style stable ID (synthesized from a per-app stable id).
 *  - `platform`: ESP32 (HA expects something here; cosmetic for our case).
 *  - `network`: wifi.
 *  - `friendly_name`: device-facing label shown in HA's discovered panel.
 *
 *  When `noiseEnabled` is true, advertises `api_encryption=Noise_NNpsk0_25519_ChaChaPoly_SHA256`. */
internal open class EsphomeMdns(
    private val context: Context,
    private val deviceInfo: EsphomeDeviceInfo,
    private val noiseEnabled: Boolean,
    private val port: Int = 6053,
) {
    private val nsd: NsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private var listener: NsdManager.RegistrationListener? = null

    open fun start() {
        if (listener != null) {
            return
        }
        val info =
            NsdServiceInfo().apply {
                serviceName = deviceInfo.name
                serviceType = "_esphomelib._tcp"
                this.port = this@EsphomeMdns.port
                setAttribute("version", deviceInfo.esphomeVersion)
                setAttribute("mac", deviceInfo.macAddress)
                setAttribute("platform", "ESP32")
                setAttribute("network", "wifi")
                setAttribute("friendly_name", deviceInfo.friendlyName)
                if (noiseEnabled) {
                    setAttribute("api_encryption", "Noise_NNpsk0_25519_ChaChaPoly_SHA256")
                }
            }
        val newListener =
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    log.i("mdns registered", "name" to serviceInfo.serviceName)
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    log.w("mdns registration failed", null, "errorCode" to errorCode)
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    log.i("mdns unregistered")
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    log.w("mdns unregistration failed", null, "errorCode" to errorCode)
                }
            }
        listener = newListener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, newListener)
    }

    open fun stop() {
        val current = listener ?: return
        runCatching { nsd.unregisterService(current) }
            .onFailure { log.w("nsd unregister threw", it) }
        listener = null
    }
}
