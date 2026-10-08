package com.shilapi.xcertplay.transport

import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbRequest
import android.os.Build
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer

internal fun queueUsbRequest(request: UsbRequest, buffer: ByteBuffer): Boolean {
    // Android 8's queue(ByteBuffer) throws above 16 KiB, unlike Android 9+.
    buffer.limit(buffer.position() + usbTransferSize(Build.VERSION.SDK_INT, buffer.remaining()))
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) request.queue(buffer)
    else {
        @Suppress("DEPRECATION")
        request.queue(buffer, buffer.remaining())
    }
}

internal fun usbTransferSize(sdk: Int, requested: Int): Int =
    if (sdk < 28) minOf(requested, 16 * 1024) else requested

/** Preserve the full USBMUX/NCM byte stream when old Android truncates large bulk writes. */
internal fun writeUsbChunks(
    size: Int,
    sdk: Int,
    timeoutMillis: Int,
    transfer: (offset: Int, count: Int, timeout: Int) -> Int,
): Int {
    val deadline = System.nanoTime() + timeoutMillis.toLong() * 1_000_000
    var offset = 0
    while (offset < size) {
        val remaining = (deadline - System.nanoTime()) / 1_000_000
        if (remaining <= 0) return if (offset == 0) -1 else offset
        val count = usbTransferSize(sdk, size - offset)
        val written = transfer(offset, count, remaining.coerceIn(1, Int.MAX_VALUE.toLong()).toInt())
        if (written <= 0) return if (offset == 0) written else offset
        offset += written
        if (written != count) return offset
    }
    return offset
}

internal fun waitForUsbRequest(connection: UsbDeviceConnection, timeoutMillis: Long): UsbRequest? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) connection.requestWait(timeoutMillis.coerceAtLeast(1))
    else connection.requestWait()

internal fun selectUsbConfiguration(
    connection: UsbDeviceConnection,
    configuration: CarPlayUsbConfiguration,
): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    selectUsbConfiguration21(connection, configuration.platformConfiguration)
} else {
    connection.controlTransfer(
        UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_STANDARD,
        9,
        configuration.id,
        0,
        null,
        0,
        1_000,
    ) >= 0
}

internal fun selectUsbInterface(connection: UsbDeviceConnection, usbInterface: UsbInterface): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) connection.setInterface(usbInterface)
    else connection.controlTransfer(
        UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_STANDARD or 1,
        11,
        IphoneCarPlayConfiguration.alternateSetting(usbInterface),
        usbInterface.id,
        null,
        0,
        1_000,
    ) >= 0

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
private fun selectUsbConfiguration21(connection: UsbDeviceConnection, value: Any?): Boolean =
    connection.setConfiguration(value as UsbConfiguration)
