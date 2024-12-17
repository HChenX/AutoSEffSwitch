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

 * Copyright (C) 2023-2024 AutoSEffSwitch Contributions
 */
package com.hchen.autoseffswitch.hook.misound;

import static com.hchen.hooktool.log.XposedLog.logI;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;

import com.hchen.autoseffswitch.hook.misound.callback.IControl;
import com.hchen.autoseffswitch.hook.misound.control.AudioEffectControl;
import com.hchen.autoseffswitch.hook.misound.control.FWAudioEffectControl;
import com.hchen.hooktool.BaseHC;
import com.hchen.hooktool.tool.additional.SystemPropTool;

import org.luckypray.dexkit.DexKitBridge;

/**
 * 新版连接耳机自动切换原声
 *
 * @author 焕晨HChen
 */
public class NewAutoSEffSwitch extends BaseHC {
    public static final String TAG = "AutoSEffSwitch";
    private Context mContext;
    public static DexKitBridge mDexKit;
    public static boolean isEarPhoneConnection = false;
    private static AudioManager mAudioManager;
    private FWAudioEffectControl mFWAudioEffectControl = null;
    private AudioEffectControl mAudioEffectControl = null;
    private IControl mControl;

    @Override
    public void init() {
        boolean isSupportFWEffect = SystemPropTool.getProp("ro.vendor.audio.fweffect", false);
        if (isSupportFWEffect) {
            mFWAudioEffectControl = new FWAudioEffectControl();
            mFWAudioEffectControl.init();
            mControl = mFWAudioEffectControl;
        } else {
            mAudioEffectControl = new AudioEffectControl();
            mAudioEffectControl.init();
            mControl = mAudioEffectControl;
        }
    }

    @Override
    protected void onApplicationAfter(Context context) {
        mContext = context;
        if (mFWAudioEffectControl != null && mFWAudioEffectControl.mAudioEffectCenter != null) {
            mFWAudioEffectControl.mAudioEffectCenterInstance = callStaticMethod(mFWAudioEffectControl.mAudioEffectCenter, "getInstance", mContext);
            logI(TAG, "mAudioEffectCenterInstance: " + mFWAudioEffectControl.mAudioEffectCenterInstance);
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        intentFilter.addAction(AudioManager.ACTION_HEADSET_PLUG);
        mContext.registerReceiver(new AudioManagerListener(), intentFilter);

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public static void updateEarPhoneState() {
        if (mAudioManager == null) return;

        boolean isBluetoothA2dpOn = mAudioManager.isBluetoothA2dpOn();
        boolean isWiredHeadsetOn = mAudioManager.isWiredHeadsetOn();
        if (isBluetoothA2dpOn || isWiredHeadsetOn) {
            isEarPhoneConnection = true;
        }
        logI(TAG, "updateEarPhoneState: isEarPhoneConnection: " + isEarPhoneConnection);
    }

    private class AudioManagerListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        logI(TAG, "ACTION_ACL_CONNECTED!");
                        isEarPhoneConnection = true;
                        mControl.updateLastEffectState();
                        mControl.setEffectToNone(mContext);
                        mControl.dumpAudioEffectState();
                    }
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        logI(TAG, "ACTION_ACL_DISCONNECTED!");
                        isEarPhoneConnection = false;
                        mControl.resetAudioEffect();
                        mControl.dumpAudioEffectState();
                    }
                    case AudioManager.ACTION_HEADSET_PLUG -> {

                        if (intent.hasExtra("state")) {
                            int state = intent.getIntExtra("state", 0);
                            if (state == 1) {
                                logI(TAG, "ACTION_HEADSET_PLUG CONNECTED!");
                                isEarPhoneConnection = true;
                                mControl.updateLastEffectState();
                                mControl.setEffectToNone(mContext);
                                mControl.dumpAudioEffectState();
                            } else if (state == 0) {
                                logI(TAG, "ACTION_HEADSET_PLUG DISCONNECTED!");
                                isEarPhoneConnection = false;
                                mControl.resetAudioEffect();
                                mControl.dumpAudioEffectState();
                            }
                        }
                    }
                }
            }
        }
    }
}
