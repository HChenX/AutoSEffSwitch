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
package com.hchen.autoseffswitch.hook.system.control;

import static com.hchen.autoseffswitch.data.EffectItem.EFFECT_DOLBY;
import static com.hchen.autoseffswitch.data.EffectItem.EFFECT_DOLBY_CONTROL;
import static com.hchen.autoseffswitch.data.EffectItem.EFFECT_MISOUND;
import static com.hchen.autoseffswitch.data.EffectItem.EFFECT_MISOUND_CONTROL;
import static com.hchen.autoseffswitch.data.EffectItem.EFFECT_SPATIAL_AUDIO;
import static com.hchen.autoseffswitch.data.EffectItem.EFFECT_SURROUND;
import static com.hchen.autoseffswitch.hook.system.AutoEffectSwitchForSystem.getEarPhoneStateFinal;
import static com.hchen.hooktool.log.XposedLog.logI;
import static com.hchen.hooktool.tool.CoreTool.callMethod;
import static com.hchen.hooktool.tool.CoreTool.callStaticMethod;
import static com.hchen.hooktool.tool.CoreTool.existsClass;
import static com.hchen.hooktool.tool.CoreTool.findClass;
import static com.hchen.hooktool.tool.CoreTool.findMethod;
import static com.hchen.hooktool.tool.CoreTool.hookMethod;
import static com.hchen.hooktool.tool.CoreTool.newInstance;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;

import com.hchen.autoseffswitch.hook.system.callback.IControlForSystem;
import com.hchen.hooktool.hook.IHook;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;

/**
 * 非 FW 模式下控制音效
 *
 * @author 焕晨HChen
 */
public class AudioEffectControlForSystem extends BaseEffectControl implements IControlForSystem {
    public static final String TAG = "AudioEffectControlForSystem";
    private static final UUID mDolbyUUID = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa");
    private static final UUID mMiSoundUUID = UUID.fromString("5b8e36a5-144a-4c38-b1d7-0002a5d5c51b");
    private static final int STATE_CHANGE_SPATIALIZER = 1;
    private static final int STATE_CHANGE_MUTED = 2;
    private final Object mLock = new Object();
    private Handler mHandler;
    private boolean mMutedInternal = false;
    private Class<?> mAudioManagerClass = null;
    private Class<?> mMiSoundClass = null;
    private Class<?> mDoblyClass = null;
    private Class<?> mIntactDolbyClass = null;
    private Object mDolbyEffectInstance = null;
    private Object mMiSoundInstance = null;
    private AudioManager mAudioManager;
    private boolean mLastDolbyEnable = false;
    private boolean mLastMiSoundEnable = false;
    private boolean mLastSpatializerEnable = false;
    private boolean mLast3dSurroundEnable = false;

    public void init() {
        mAudioManagerClass = findClass("android.media.AudioManager").get();
        mMiSoundClass = findClass("android.media.audiofx.MiSound").get();
        if (existsClass("com.dolby.dax.DolbyAudioEffect"))
            mIntactDolbyClass = findClass("com.dolby.dax.DolbyAudioEffect").get();
        else
            mDoblyClass = findClass("com.android.server.audio.dolbyeffect.DolbyEffectController$DolbyAudioEffectHelper").get();

        hookMethod("android.media.audiofx.AudioEffect",
                "setEnabled",
                boolean.class,
                new IHook() {
                    @Override
                    public void before() {
                        observeCall();
                        if (!getEarPhoneStateFinal()) return;

                        if (mDolbyEffectInstance != null) {
                            if (Objects.equals(mDolbyEffectInstance, thisObject())) {
                                logI(TAG, "earphone is connection, skip set dolby effect!!");
                                setResult(0); // SUCCESS
                                return;
                            }
                        }

                        if (mMiSoundInstance != null) {
                            if (Objects.equals(mMiSoundInstance, thisObject())) {
                                logI(TAG, "earphone is connection, skip set misound effect!!");
                                setResult(0); // SUCCESS
                            }
                        }
                    }
                }
        );

        hookMethod("android.media.Spatializer",
                "setEnabled",
                boolean.class,
                new IHook() {
                    @Override
                    public void before() {
                        if (getEarPhoneStateFinal()) {
                            logI(TAG, "earphone is connection, skip set spatializer effect!!");
                            returnNull();
                        }
                    }
                }
        );

        hookMethod("android.media.audiofx.MiSound",
                "set3dSurround",
                int.class,
                new IHook() {
                    @Override
                    public void before() {
                        if (getEarPhoneStateFinal()) {
                            logI(TAG, "earphone is connection, skip set 3dSurround effect!!");
                            returnNull();
                        }
                    }
                }
        );
    }

    public void initEffect(Context context, AudioManager audioManager) {
        mAudioManager = audioManager;
        mDolbyEffectInstance = newInstanceDolbyEffect();
        mMiSoundInstance = newInstanceMiSound();

        mHandler = new Handler(context.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case STATE_CHANGE_SPATIALIZER -> {
                        Object sService = callStaticMethod(mAudioManagerClass, "getService");
                        callMethod(sService, "setSpatializerEnabled", msg.obj);

                        if (mMutedInternal) {
                            mHandler.sendMessageDelayed(mHandler.obtainMessage(STATE_CHANGE_MUTED), 200L);
                        }
                    }
                    case STATE_CHANGE_MUTED -> {
                        muteMusicStream(false);
                    }
                }
            }
        };
    }

    private boolean isIntactDolbyClass() {
        return mIntactDolbyClass != null;
    }

    private void releaseDolbyEffectIfNeed() {
        if (mDolbyEffectInstance == null)
            mDolbyEffectInstance = newInstanceDolbyEffect();

        if (mDolbyEffectInstance == null) return;
        if (hasControlDolby()) return;

        callMethod(mDolbyEffectInstance, "release");
        mDolbyEffectInstance = null;
        mDolbyEffectInstance = newInstanceDolbyEffect();
    }

    private void releaseMiSoundIfNeed() {
        if (mMiSoundInstance == null)
            mMiSoundInstance = newInstanceMiSound();

        if (mMiSoundInstance == null) return;
        if (hasControlMiSound()) return;

        callMethod(mMiSoundInstance, "release");
        mMiSoundInstance = null;
        mMiSoundInstance = newInstanceMiSound();
    }

    private Object newInstanceDolbyEffect() {
        if (mDoblyClass == null && mIntactDolbyClass == null) return null;
        if (isIntactDolbyClass())
            return newInstance(mIntactDolbyClass, 0, 0);
        else
            return newInstance(mDoblyClass, 0, 0);
    }

    private Object newInstanceMiSound() {
        if (mMiSoundClass == null) return null;
        return newInstance(mMiSoundClass, 0, 0);
    }

    private boolean hasControlDolby() {
        if (mDolbyEffectInstance == null) return false;
        return (boolean) callMethod(mDolbyEffectInstance, "hasControl");
    }

    private boolean hasControlMiSound() {
        if (mMiSoundInstance == null) return false;
        return (boolean) callMethod(mMiSoundInstance, "hasControl");
    }

    private void setEnableDolbyEffect(boolean enable) {
        if (mDolbyEffectInstance == null) return;
        releaseDolbyEffectIfNeed();

        if (isIntactDolbyClass()) {
            callMethod(mDolbyEffectInstance, "setBoolParam", 0, enable);
        } else {
            byte[] bArr = new byte[12];
            int int32ToByteArray = 0 + int32ToByteArray(0, bArr, 0);
            int32ToByteArray(enable ? 1 : 0, bArr, int32ToByteArray + int32ToByteArray(1, bArr, int32ToByteArray));
            callMethod(mDolbyEffectInstance, "checkReturnValue", callMethod(mDolbyEffectInstance, "setParameter", 5, bArr));
        }

        setEnableEffect(mDolbyEffectInstance, enable);
    }

    private int int32ToByteArray(int src, byte[] dst, int index) {
        if (isIntactDolbyClass())
            return (int) callStaticMethod(mIntactDolbyClass, "int32ToByteArray", src, dst, index);
        else
            return (int) callStaticMethod(mDoblyClass, "int32ToByteArray", src, dst, index);
    }

    private void setEnableMiSound(boolean enable) {
        if (mMiSoundInstance == null) return;
        releaseMiSoundIfNeed();

        callMethod(mMiSoundInstance, "checkStatus", callMethod(mMiSoundInstance, "setParameter", 25, enable ? 1 : 0));
        setEnableEffect(mMiSoundInstance, enable);
    }

    private void setEnableEffect(Object instance, boolean enable) {
        if (instance == null || instance.getClass().getSuperclass() == null) return;
        Class<?> superEffectClass = instance.getClass().getSuperclass();
        Method native_setEnabled = findMethod(superEffectClass, "native_setEnabled", boolean.class).get();
        callMethod(mMiSoundInstance, "checkState", "setEnabled()");
        callMethod(instance, native_setEnabled, enable); // super private
    }

    private void setEnable3dSurround(boolean enable) {
        if (mMiSoundInstance == null) return;
        callMethod(mMiSoundInstance, "checkStatus", callMethod(mMiSoundInstance, "setParameter", 20, enable ? 1 : 0));
    }

    private void setEnableSpatializer(boolean enable) {
        synchronized (mLock) {
            if (mAudioManagerClass == null) return;
            if (!isAvailableSpatializer()) return;
            boolean alreadyMuted = this.mAudioManager.isStreamMute(3);
            if (!alreadyMuted || mMutedInternal) {
                mHandler.removeMessages(STATE_CHANGE_MUTED);
                muteMusicStream(true);
            }
            mHandler.removeMessages(STATE_CHANGE_SPATIALIZER);
            mHandler.sendMessageDelayed(mHandler.obtainMessage(STATE_CHANGE_SPATIALIZER, enable), 100L);
        }
    }

    private void muteMusicStream(boolean muted) {
        synchronized (mLock) {
            mMutedInternal = muted;
            int direction = muted ? -100 : 100;
            this.mAudioManager.adjustStreamVolume(3, direction, 0);
        }
    }

    private boolean isAvailableSpatializer() {
        if (mAudioManagerClass == null) return false;
        Object sService = callStaticMethod(mAudioManagerClass, "getService");
        return (boolean) callMethod(sService, "isSpatializerAvailable");
    }

    private boolean isEnabledDolbyEffect() {
        if (mDolbyEffectInstance == null) return false;
        releaseDolbyEffectIfNeed();

        if (isIntactDolbyClass()) {
            return (boolean) callMethod(mDolbyEffectInstance, "getBoolParam", 0);
        } else {
            byte[] baValue = new byte[12];
            int32ToByteArray(0, baValue, 0);
            callMethod(mDolbyEffectInstance, "checkReturnValue", callMethod(mDolbyEffectInstance, "getParameter", 0 + 5, baValue));
            int en = byteArrayToInt32(baValue);
            return en > 0;
        }
    }

    private static int byteArrayToInt32(byte[] ba) {
        return ((ba[3] & 255) << 24) | ((ba[2] & 255) << 16) | ((ba[1] & 255) << 8) | (ba[0] & 255);
    }

    private boolean isEnabledMiSound() {
        if (mMiSoundClass == null) return false;
        releaseMiSoundIfNeed();

        return (boolean) callMethod(mMiSoundInstance, "getEnabled");
    }

    private boolean isEnabled3dSurround() {
        if (mMiSoundInstance == null) return false;
        int[] value = new int[1];
        callMethod(mMiSoundInstance, "checkStatus", callMethod(mMiSoundInstance, "getParameter", 20, value));
        return value[0] == 1;
    }

    private boolean isEnabledSpatializer() {
        if (mAudioManagerClass == null) return false;
        Object sService = callStaticMethod(mAudioManagerClass, "getService");
        return (boolean) callMethod(sService, "isSpatializerEnabled");
    }

    @Override
    void updateEffectMap() {
        mEffectHasControlMap.clear();
        mEffectHasControlMap.put(EFFECT_DOLBY_CONTROL, String.valueOf(hasControlDolby()));
        mEffectHasControlMap.put(EFFECT_MISOUND_CONTROL, String.valueOf(hasControlMiSound()));

        mEffectEnabledMap.clear();
        mEffectEnabledMap.put(EFFECT_DOLBY, String.valueOf(isEnabledDolbyEffect()));
        mEffectEnabledMap.put(EFFECT_MISOUND, String.valueOf(isEnabledMiSound()));
        mEffectEnabledMap.put(EFFECT_SPATIAL_AUDIO, String.valueOf(isEnabledSpatializer()));
        mEffectEnabledMap.put(EFFECT_SURROUND, String.valueOf(isEnabled3dSurround()));
    }

    @Override
    public void updateLastEffectState() {
        mLastDolbyEnable = isEnabledDolbyEffect();
        mLastMiSoundEnable = isEnabledMiSound();
        mLastSpatializerEnable = isEnabledSpatializer();
        mLast3dSurroundEnable = isEnabled3dSurround();

        logI(TAG, "updateLastEffectState: mLastDolbyEnable: " + mLastDolbyEnable + ", mLastMiSoundEnable: " + mLastMiSoundEnable
                + ", mLastSpatializerEnable: " + mLastSpatializerEnable + ", mLast3dSurroundEnable: " + mLast3dSurroundEnable);
    }

    @Override
    public void setEffectToNone(Context context) {
        setEnableDolbyEffect(false);
        setEnableMiSound(false);
        setEnableSpatializer(false);
        setEnable3dSurround(false);
    }

    @Override
    public void resetAudioEffect() {
        if (mLastDolbyEnable)
            setEnableDolbyEffect(true);
        else if (mLastMiSoundEnable) {
            setEnableMiSound(true);
        } else if (mLastSpatializerEnable) {
            setEnableSpatializer(true);
        } else if (mLast3dSurroundEnable) {
            setEnable3dSurround(true);
        }
    }

    @Override
    public void dumpAudioEffectState() {
        logI(TAG, "dolby: " + isEnabledDolbyEffect() + ", misound: " + isEnabledMiSound() +
                ", spatializer: " + isEnabledSpatializer() + ", 3dSurround: " + isEnabled3dSurround());
    }
}

