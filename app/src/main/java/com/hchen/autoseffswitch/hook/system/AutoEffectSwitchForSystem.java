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
package com.hchen.autoseffswitch.hook.system;

import static com.hchen.autoseffswitch.hook.system.AutoEffectSwitchForSystem.EarphoneBroadcastReceiver.DUMP;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;

import androidx.annotation.NonNull;

import com.hchen.autoseffswitch.hook.misound.callback.IControl;
import com.hchen.autoseffswitch.hook.system.binder.EffectInfoService;
import com.hchen.autoseffswitch.hook.system.control.AudioEffectControlForSystem;
import com.hchen.autoseffswitch.hook.system.control.FWAudioEffectControlForSystem;
import com.hchen.hooktool.BaseHC;
import com.hchen.hooktool.hook.IHook;
import com.hchen.hooktool.tool.additional.SystemPropTool;

/**
 * 自动切换音效-系统框架端-总控制端
 *
 * @author 焕晨HChen
 */
public class AutoEffectSwitchForSystem extends BaseHC {
    public static final String TAG = "AutoSEffSwitch";
    public Context mContext;
    public static EffectInfoService mEffectInfoService = null;
    private AudioEffectControlForSystem mAudioEffectControlForSystem = null;
    private FWAudioEffectControlForSystem mFWAudioEffectControlForSystem = null;
    public static boolean isEarphoneConnection = false;
    private IControl mIControl = null;

    @Override
    protected void init() {
        if (isSupportFW()) {
            mFWAudioEffectControlForSystem = new FWAudioEffectControlForSystem();
            mFWAudioEffectControlForSystem.init();
            mIControl = mFWAudioEffectControlForSystem;
        } else {
            mAudioEffectControlForSystem = new AudioEffectControlForSystem();
            mAudioEffectControlForSystem.init();
            mIControl = mAudioEffectControlForSystem;
        }

        hookMethod("com.android.server.am.ActivityManagerService",
                "systemReady",
                Runnable.class, "com.android.server.utils.TimingsTraceAndSlog",
                new IHook() {
                    @Override
                    public void after() {
                        mContext = (Context) getThisField("mContext");
                        if (mContext == null) return; // 不可能会是 null 吧??
                        if (mEffectInfoService == null) {
                            if (mFWAudioEffectControlForSystem != null)
                                mEffectInfoService = new EffectInfoService(mFWAudioEffectControlForSystem);
                            else if (mAudioEffectControlForSystem != null)
                                mEffectInfoService = new EffectInfoService(mAudioEffectControlForSystem);
                        }
                        registerEarphoneReceiver();
                        registerDebug();

                        if (mAudioEffectControlForSystem == null) return;
                        mAudioEffectControlForSystem.setContext(mContext);
                    }
                }
        );
    }

    private boolean isSupportFW() {
        return SystemPropTool.getProp("ro.vendor.audio.fweffect", false);
    }

    private void registerDebug() {
        if (mContext == null) return;

        // 用于因为某些可能的情况导致 isEarphoneConnection 一直为 true 的情况。
        Settings.Global.putInt(mContext.getContentResolver(), "auto_effect_switch_restore_earphone_state", 0);
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor("auto_effect_switch_restore_earphone_state"),
                false,
                new ContentObserver(new Handler(mContext.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        if (selfChange) return;
                        if (Settings.Global.getInt(mContext.getContentResolver(), "auto_effect_switch_restore_earphone_state", 0) == 1) {
                            isEarphoneConnection = false;
                            Settings.Global.putInt(mContext.getContentResolver(), "auto_effect_switch_restore_earphone_state", 0);
                        }
                    }
                }
        );
    }

    private void reportEarphoneState() {
        if (mContext == null)
            Settings.Global.putInt(mContext.getContentResolver(), "auto_effect_switch_earphone_state", isEarphoneConnection ? 1 : 0);
    }

    private void registerEarphoneReceiver() {
        if (mContext == null) return;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        intentFilter.addAction(AudioManager.ACTION_HEADSET_PLUG);
        mContext.registerReceiver(new EarphoneBroadcastReceiver(), intentFilter);
    }

    class EarphoneBroadcastReceiver extends BroadcastReceiver {
        private DumpHandler mDumpHandler;
        public static final int DUMP = 0;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mDumpHandler == null)
                mDumpHandler = new DumpHandler(context.getMainLooper());

            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        isEarphoneConnection = true;
                        reportEarphoneState();
                        mIControl.setEffectToNone(context);
                        dump();
                    }
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        isEarphoneConnection = false;
                        reportEarphoneState();
                        mIControl.resetAudioEffect();
                        dump();
                    }
                    case AudioManager.ACTION_HEADSET_PLUG -> {
                        if (intent.hasExtra("state")) {
                            int state = intent.getIntExtra("state", 0);
                            if (state == 1) {
                                isEarphoneConnection = true;
                                reportEarphoneState();
                                mIControl.setEffectToNone(context);
                                dump();
                            } else if (state == 0) {
                                isEarphoneConnection = false;
                                reportEarphoneState();
                                mIControl.resetAudioEffect();
                                dump();
                            }
                        }
                    }
                }
            }
        }

        private void dump() {
            if (mDumpHandler.hasMessages(DUMP))
                mDumpHandler.removeMessages(DUMP);
            mDumpHandler.sendEmptyMessageDelayed(DUMP, 1000);
        }
    }

    class DumpHandler extends Handler {
        public DumpHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            int what = msg.what;
            if (what == DUMP) {
                mIControl.dumpAudioEffectState();
            }
        }
    }
}
