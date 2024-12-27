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
import static com.hchen.hooktool.tool.CoreTool.findClass;
import static com.hchen.hooktool.tool.CoreTool.hookConstructor;
import static com.hchen.hooktool.tool.CoreTool.hookMethod;

import android.content.Context;

import com.hchen.autoseffswitch.hook.system.callback.IControl;
import com.hchen.hooktool.hook.IHook;

import java.util.Objects;
import java.util.UUID;

/**
 * 非 FW 模式下控制音效
 *
 * @author 焕晨HChen
 */
public class AudioEffectControlForSystem extends BaseEffectControl implements IControl {
    public static final String TAG = "AudioEffectControlForSystem";
    private Class<?> mAudioManagerClass = null;
    private static final UUID mDolbyUUID = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa");
    private static final UUID mMiSoundUUID = UUID.fromString("5b8e36a5-144a-4c38-b1d7-0002a5d5c51b");
    private Object mDolbyEffect = null;
    private Object mMiSoundEffect = null;
    private boolean mLastDolbyEnable = false;
    private boolean mLastMiSoundEnable = false;
    private boolean mLastSpatializerEnable = false;
    private boolean mLast3dSurroundEnable = false;

    public void init() {
        mAudioManagerClass = findClass("android.media.AudioManager").get();

        hookConstructor("android.media.audiofx.AudioEffect",
                UUID.class, UUID.class, int.class, int.class, "android.media.AudioDeviceAttributes", boolean.class,
                new IHook() {
                    @Override
                    public void after() {
                        observeCall();
                        UUID mUUID = (UUID) getArgs(1);
                        if (mUUID == null) return;
                        if (mUUID.equals(mDolbyUUID)) {
                            mDolbyEffect = thisObject();
                        } else if (mUUID.equals(mMiSoundUUID)) {
                            mMiSoundEffect = thisObject();
                        }
                    }
                }
        );

        hookMethod("android.media.audiofx.AudioEffect",
                "setEnabled",
                boolean.class,
                new IHook() {
                    @Override
                    public void before() {
                        observeCall();
                        if (!getEarPhoneStateFinal()) return;

                        if (mDolbyEffect == null) return;
                        if (Objects.equals(mDolbyEffect, thisObject())) {
                            logI(TAG, "earphone is connection, skip set dolby effect!!");
                            setResult(0); // SUCCESS
                            return;
                        }

                        if (mMiSoundEffect == null) return;
                        if (Objects.equals(mMiSoundEffect, thisObject())) {
                            logI(TAG, "earphone is connection, skip set misound effect!!");
                            setResult(0); // SUCCESS
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
                        observeCall();
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
                        observeCall();
                        if (getEarPhoneStateFinal()) {
                            logI(TAG, "earphone is connection, skip set 3dSurround effect!!");
                            returnNull();
                        }
                    }
                }
        );
    }

    private boolean hasControlDolby() {
        if (mDolbyEffect == null) return false;
        return (boolean) callMethod(mDolbyEffect, "hasControl");
    }

    private boolean hasControlMiSound() {
        if (mMiSoundEffect == null) return false;
        return (boolean) callMethod(mMiSoundEffect, "hasControl");
    }

    private void setEnableDolbyEffect(boolean enable) {
        if (mDolbyEffect == null) return;
        callMethod(mDolbyEffect, "checkState", "setEnabled()");
        callMethod(mMiSoundEffect, "native_setEnabled", enable);
    }

    private void setEnableMiSound(boolean enable) {
        if (mMiSoundEffect == null) return;
        callMethod(mMiSoundEffect, "checkState", "setEnabled()");
        callMethod(mMiSoundEffect, "native_setEnabled", enable);
    }

    private void setEnable3dSurround(boolean enable) {
        if (mMiSoundEffect == null) return;
        callMethod(mMiSoundEffect, "checkStatus", callMethod(mMiSoundEffect, "setParameter", 20, enable ? 1 : 0));
    }

    private void setEnableSpatializer(boolean enable) {
        if (mAudioManagerClass == null) return;
        if (!isAvailableSpatializer()) return;
        Object sService = callStaticMethod(mAudioManagerClass, "getService");
        callMethod(sService, "setSpatializerEnabled", enable);
    }

    private boolean isAvailableSpatializer() {
        if (mAudioManagerClass == null) return false;
        Object sService = callStaticMethod(mAudioManagerClass, "getService");
        return (boolean) callMethod(sService, "isSpatializerAvailable");
    }

    private boolean isEnabledDolbyEffect() {
        if (mDolbyEffect == null) return false;
        return (boolean) callMethod(mDolbyEffect, "getEnabled");
    }

    private boolean isEnabledMiSound() {
        if (mMiSoundEffect == null) return false;
        return (boolean) callMethod(mMiSoundEffect, "getEnabled");
    }

    private boolean isEnabled3dSurround() {
        if (mMiSoundEffect == null) return false;
        int[] value = new int[1];
        callMethod(mMiSoundEffect, "checkStatus", callMethod(mMiSoundEffect, "getParameter", 20, value));
        return value[0] == 1;
    }

    private boolean isEnabledSpatializer() {
        if (mAudioManagerClass == null) return false;
        Object sService = callStaticMethod(mAudioManagerClass, "getService");
        return (boolean) callMethod(sService, "isSpatializerEnabled");
    }

    @Override
    void updateEffectMap() {
        mEffectEnabledMap.clear();
        mEffectEnabledMap.put(EFFECT_DOLBY, String.valueOf(isEnabledDolbyEffect()));
        mEffectEnabledMap.put(EFFECT_MISOUND, String.valueOf(isEnabledMiSound()));
        mEffectEnabledMap.put(EFFECT_SPATIAL_AUDIO, String.valueOf(isEnabledSpatializer()));
        mEffectEnabledMap.put(EFFECT_SURROUND, String.valueOf(isEnabled3dSurround()));

        mEffectHasControlMap.clear();
        mEffectHasControlMap.put(EFFECT_DOLBY_CONTROL, String.valueOf(hasControlDolby()));
        mEffectHasControlMap.put(EFFECT_MISOUND_CONTROL, String.valueOf(hasControlMiSound()));
    }

    @Override
    public void updateLastEffectState() {
        mLastDolbyEnable = isEnabledDolbyEffect();
        mLastMiSoundEnable = isEnabledMiSound();
        mLastSpatializerEnable = isEnabledSpatializer();
        mLast3dSurroundEnable = isEnabled3dSurround();
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

