package com.tglabs.spotivibe.data

import android.media.AudioDeviceInfo
import com.tglabs.spotivibe.domain.OutputKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Konstanta `AudioDeviceInfo.TYPE_*` adalah `static final int`, jadi nilainya
 * tertanam saat kompilasi dan test ini jalan di JVM tanpa stub Android.
 */
class AudioOutputKindTest {

    @Test
    fun `bluetooth media dikenali sebagai bluetooth`() {
        listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID,
        ).forEach { assertEquals(OutputKind.Bluetooth, outputKindOf(it)) }
    }

    @Test
    fun `sco panggilan bukan output musik`() {
        assertNull(outputKindOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
    }

    @Test
    fun `earpiece dan submix diabaikan`() {
        assertNull(outputKindOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
        assertNull(outputKindOf(AudioDeviceInfo.TYPE_REMOTE_SUBMIX))
    }

    @Test
    fun `bus audio mobil dihitung eksternal`() {
        assertEquals(OutputKind.External, outputKindOf(AudioDeviceInfo.TYPE_BUS))
    }

    @Test
    fun `headset kabel dan usb dihitung kabel`() {
        assertEquals(OutputKind.Wired, outputKindOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertEquals(OutputKind.Wired, outputKindOf(AudioDeviceInfo.TYPE_USB_HEADSET))
    }

    @Test
    fun `speaker internal paling akhir`() {
        assertEquals(OutputKind.BuiltIn, outputKindOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
    }
}
