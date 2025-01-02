package com.hchen.autoseffswitch.hook.systemui;

import static com.hchen.hooktool.log.XposedLog.logE;
import static com.hchen.hooktool.log.XposedLog.logI;
import static com.hchen.hooktool.log.XposedLog.logW;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.RemoteException;

import com.hchen.autoseffswitch.IEffectInfo;
import com.hchen.hooktool.BaseHC;
import com.hchen.hooktool.hook.IHook;
import com.hchen.hooktool.tool.additional.SystemPropTool;

public class AutoSEffSwitchForSystemUi extends BaseHC {
    private static final String TAG = "AutoSEffSwitchForSystemUi";
    private Context mContext;
    private static IEffectInfo mIEffectInfo;

    @Override
    protected void init() {
        if (isSupportFW()) {
            onSupportFW();
        } else {

        }
    }

    public static boolean isSupportFW() {
        return SystemPropTool.getProp("ro.vendor.audio.fweffect", false);
    }

    @Override
    protected void onApplicationAfter(Context context) {
        mContext = context;

        Intent intent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return;
        Bundle bundle = intent.getBundleExtra("effect_info");
        if (bundle == null) return;
        mIEffectInfo = IEffectInfo.Stub.asInterface(bundle.getBinder("effect_info"));
        logI(TAG, "onApplicationAfter: EffectInfoService: " + mIEffectInfo);
    }

    public static boolean getEarPhoneStateFinal() {
        if (mIEffectInfo != null) {
            try {
                return mIEffectInfo.isEarphoneConnection();
            } catch (RemoteException e) {
                logE(TAG, e);
                return false;
            }
        }
        logW(TAG, "getEarPhoneStateFinal: mIEffectInfo is null!!");
        return false;
    }

    private void onSupportFW() {
        hookMethod("android.media.audiofx.AudioEffectCenter",
                "setEffectActive",
                String.class, boolean.class,
                new IHook() {
                    @Override
                    public void before() {
                        if (getEarPhoneStateFinal()) {
                            logI(TAG, "earphone is connection, skip set effect: " + getArgs(0) + "!!");
                            returnNull();
                        }
                    }
                }
        );
    }

    private void onNotSupportFW() {

    }
}
