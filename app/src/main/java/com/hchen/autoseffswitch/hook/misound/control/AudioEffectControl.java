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
package com.hchen.autoseffswitch.hook.misound.control;

import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.TAG;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.isEarPhoneConnection;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.mDexKit;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.updateEarPhoneState;
import static com.hchen.hooktool.BaseHC.classLoader;
import static com.hchen.hooktool.log.XposedLog.logE;
import static com.hchen.hooktool.log.XposedLog.logI;
import static com.hchen.hooktool.tool.CoreTool.callMethod;
import static com.hchen.hooktool.tool.CoreTool.findClass;
import static com.hchen.hooktool.tool.CoreTool.getField;
import static com.hchen.hooktool.tool.CoreTool.hook;
import static com.hchen.hooktool.tool.CoreTool.newInstance;

import android.content.Context;

import com.hchen.autoseffswitch.hook.misound.callback.IControl;
import com.hchen.hooktool.hook.IHook;

import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 老版本的切换逻辑
 *
 * @author 焕晨HChen
 */
public class AudioEffectControl implements IControl {
    private Class<?> mDolbyEffect;
    private Class<?> mMiSound;
    private Object mDolbyEffectInstance = null;
    private Object mMiSoundInstance = null;
    private boolean mLastDolbyEnabled = false;
    private boolean mLastMiSoundEnabled = false;
    private Object effectSelectionPrefs;
    private Method mSetEnableMethod = null;

    public void init() {
        try {
            mDolbyEffect = mDexKit.findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                            .usingStrings("Creating a DolbyAudioEffect to global output mix!")
                    )).singleOrNull().getInstance(classLoader);
            mMiSound = findClass("android.media.audiofx.MiSound").get();

            for (Method method : mDolbyEffect.getDeclaredMethods()) {
                if (method.getParameterTypes().length > 0 && method.getParameterTypes()[0].equals(boolean.class)) {
                    mSetEnableMethod = method;
                    break;
                }
            }

            mDolbyEffectInstance = newInstanceDolbyEffect();
            mMiSoundInstance = newInstanceMiSound();

            Method dolbySwitch = mDexKit.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass(ClassMatcher.create().usingStrings("setDsOnSafely: enter"))
                            .usingStrings("setDsOnSafely: enter")
                    )
            ).singleOrNull().getMethodInstance(classLoader);
            hook(dolbySwitch, new IHook() {
                @Override
                public void before() {
                    if (isEarPhoneConnection) {
                        returnNull();
                        logI(TAG, "Don't set dolby mode, in earphone mode!");
                    }
                }
            });

            Method miSoundSwitch = mDexKit.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass(ClassMatcher.create().usingStrings("setEffectEnable() fail, exception: "))
                            .usingStrings("setEffectEnable() fail, exception: ")
                    )
            ).singleOrNull().getMethodInstance(classLoader);
            hook(miSoundSwitch, new IHook() {
                @Override
                public void before() {
                    if (isEarPhoneConnection) {
                        returnNull();
                        logI(TAG, "Don't set misound mode, in earphone mode!");
                    }
                }
            });

            Method headsetSettings = mDexKit.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass(ClassMatcher.create().usingStrings("supports spatial audio 3.0 "))
                            .usingStrings("supports spatial audio 3.0 ")
                    )
            ).singleOrNull().getMethodInstance(classLoader);
            Field effectSelectionField = mDexKit.findField(FindField.create()
                    .matcher(FieldMatcher.create()
                            .declaredClass(ClassMatcher.create().usingStrings("supports spatial audio 3.0 "))
                            .type(findClass("miuix.preference.DropDownPreference").get())
                            .addReadMethod(MethodMatcher.create()
                                    .declaredClass(ClassMatcher.create().usingStrings("supports spatial audio 3.0 "))
                                    .usingStrings("updateEffectSelectionPreference(): set as ")
                            )
                    )).singleOrNull().getFieldInstance(classLoader);
            hook(headsetSettings, new IHook() {
                @Override
                public void after() {
                    effectSelectionPrefs = getField(thisObject(), effectSelectionField);
                    updateEarPhoneState();
                    updateEffectSelectionState();
                }
            });

            Method broadcastReceiver = mDexKit.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass(ClassMatcher.create().usingStrings("onReceive: switch effect when bt connected"))
                            .usingStrings("onReceive: switch effect when bt connected")
                    )
            ).singleOrNull().getMethodInstance(classLoader);
            hook(broadcastReceiver, new IHook() {
                @Override
                public void before() {
                    updateEffectSelectionState();
                }
            });
        } catch (Throwable e) {
            logE(TAG, e);
        }
    }

    private void updateEffectSelectionState() {
        if (isEarPhoneConnection) {
            callMethod(effectSelectionPrefs, "setEnabled", false);
            logI(TAG, "Disable effect selection: " + effectSelectionPrefs);
        } else
            callMethod(effectSelectionPrefs, "setEnabled", true);
    }

    private void releaseDolbyEffectIfNeed() {
        if (mDolbyEffectInstance == null)
            mDolbyEffectInstance = newInstanceDolbyEffect();

        if (mDolbyEffectInstance == null) return;
        if (hasControlDolbyEffect()) return;

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
        if (mDolbyEffect == null) return null;
        return newInstance(mDolbyEffect, 0, 0);
    }

    private Object newInstanceMiSound() {
        if (mMiSound == null) return null;
        return newInstance(mMiSound, 0, 0);
    }

    private boolean hasControlDolbyEffect() {
        if (mDolbyEffectInstance == null) return false;
        return (boolean) callMethod(mDolbyEffectInstance, "hasControl");
    }

    private boolean hasControlMiSound() {
        if (mMiSoundInstance == null) return false;
        return (boolean) callMethod(mMiSoundInstance, "hasControl");
    }

    private void setEnableDolbyEffect(boolean enable) {
        releaseDolbyEffectIfNeed();

        if (mDolbyEffectInstance == null) return;
        if (mSetEnableMethod == null) return;
        callMethod(mDolbyEffectInstance, mSetEnableMethod, enable);
    }

    private void setEnableMiSound(boolean enable) {
        releaseMiSoundIfNeed();

        if (mMiSoundInstance == null) return;
        callMethod(mMiSoundInstance, "setEnabled", enable);
    }

    private boolean isEnableDolbyEffect() {
        releaseDolbyEffectIfNeed();

        if (mDolbyEffectInstance == null) return false;
        return (boolean) callMethod(mDolbyEffectInstance, "getEnabled");
    }

    private boolean isEnableMiSound() {
        releaseMiSoundIfNeed();

        if (mMiSoundInstance == null) return false;
        return (boolean) callMethod(mMiSoundInstance, "getEnabled");
    }

    @Override
    public void updateLastEffectState() {
        mLastDolbyEnabled = false;
        mLastMiSoundEnabled = false;

        mLastDolbyEnabled = isEnableDolbyEffect();
        mLastMiSoundEnabled = isEnableMiSound();
    }

    @Override
    public void setEffectToNone(Context context) {
        setEnableDolbyEffect(false);
        setEnableMiSound(false);
    }

    @Override
    public void resetAudioEffect() {
        if (mLastDolbyEnabled) {
            setEnableMiSound(false);
            setEnableDolbyEffect(true);
        } else if (mLastMiSoundEnabled) {
            setEnableMiSound(true);
            setEnableDolbyEffect(false);
        } else {
            setEnableDolbyEffect(true);
            setEnableMiSound(false);
        }
    }

    @Override
    public void dumpAudioEffectState() {
        logI(TAG, "dolby state: " + isEnableDolbyEffect() + ", misound state: " + isEnableMiSound());
    }
}
