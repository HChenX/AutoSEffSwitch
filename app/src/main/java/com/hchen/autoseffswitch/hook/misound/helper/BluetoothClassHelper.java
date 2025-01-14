/*
 * This file is part of AutoSEffSwitch.

 * AutoSEffSwitch is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2023-2024 HChenX
 */
package com.hchen.autoseffswitch.hook.misound.helper;

import android.bluetooth.BluetoothClass;

/**
 * 蓝牙状态判断
 *
 * @deprecated
 */
@Deprecated
public class BluetoothClassHelper {
    public static final int PROFILE_HEADSET = 0;
    public static final int PROFILE_A2DP = 1;
    private static final int PROFILE_OPP = 2;
    private static final int PROFILE_HID = 3;
    private static final int PROFILE_PANU = 4;
    private static final int PROFILE_NAP = 5;
    private static final int PROFILE_A2DP_SINK = 6;

    /**
     * Check class bits for possible bluetooth profile support.
     * This is a simple heuristic that tries to guess if a device with the
     * given class bits might support specified profile. It is not accurate for all
     * devices. It tries to err on the side of false positives.
     *
     * @param profile the profile to be checked
     * @return whether this device supports specified profile
     */
    public static boolean doesClassMatch(BluetoothClass bluetoothClass, int profile) {
        if (profile == PROFILE_A2DP) {
            if (bluetoothClass.hasService(BluetoothClass.Service.RENDER)) {
                return true;
            }
            // By the A2DP spec, sinks must indicate the RENDER service.
            // However we found some that do not (Chordette). So lets also
            // match on some other class bits.
            switch (bluetoothClass.getDeviceClass()) {
                case BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO:
                case BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES:
                case BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER:
                case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO:
                    return true;
                default:
                    return false;
            }
        } else if (profile == PROFILE_A2DP_SINK) {
            if (bluetoothClass.hasService(BluetoothClass.Service.CAPTURE)) {
                return true;
            }
            // By the A2DP spec, srcs must indicate the CAPTURE service.
            // However if some device that do not, we try to
            // match on some other class bits.
            switch (bluetoothClass.getDeviceClass()) {
                case BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO:
                case BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX:
                case BluetoothClass.Device.AUDIO_VIDEO_VCR:
                    return true;
                default:
                    return false;
            }
        } else if (profile == PROFILE_HEADSET) {
            // The render service class is required by the spec for HFP, so is a
            // pretty good signal
            if (bluetoothClass.hasService(BluetoothClass.Service.RENDER)) {
                return true;
            }
            // Just in case they forgot the render service class
            switch (bluetoothClass.getDeviceClass()) {
                case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
                case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET:
                case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO:
                    return true;
                default:
                    return false;
            }
        } else if (profile == PROFILE_OPP) {
            if (bluetoothClass.hasService(BluetoothClass.Service.OBJECT_TRANSFER)) {
                return true;
            }

            switch (bluetoothClass.getDeviceClass()) {
                case BluetoothClass.Device.COMPUTER_UNCATEGORIZED:
                case BluetoothClass.Device.COMPUTER_DESKTOP:
                case BluetoothClass.Device.COMPUTER_SERVER:
                case BluetoothClass.Device.COMPUTER_LAPTOP:
                case BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA:
                case BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA:
                case BluetoothClass.Device.COMPUTER_WEARABLE:
                case BluetoothClass.Device.PHONE_UNCATEGORIZED:
                case BluetoothClass.Device.PHONE_CELLULAR:
                case BluetoothClass.Device.PHONE_CORDLESS:
                case BluetoothClass.Device.PHONE_SMART:
                case BluetoothClass.Device.PHONE_MODEM_OR_GATEWAY:
                case BluetoothClass.Device.PHONE_ISDN:
                    return true;
                default:
                    return false;
            }
        } else if (profile == PROFILE_HID) {
            return bluetoothClass.getMajorDeviceClass() == BluetoothClass.Device.Major.PERIPHERAL;
        } else if (profile == PROFILE_PANU || profile == PROFILE_NAP) {
            // No good way to distinguish between the two, based on class bits.
            if (bluetoothClass.hasService(BluetoothClass.Service.NETWORKING)) {
                return true;
            }
            return bluetoothClass.getMajorDeviceClass() == BluetoothClass.Device.Major.NETWORKING;
        } else {
            return false;
        }
    }
}
