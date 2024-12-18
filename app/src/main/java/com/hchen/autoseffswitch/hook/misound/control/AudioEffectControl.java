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
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.getEarPhoneState;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.isEarPhoneConnection;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.isSupportFW;
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
import android.os.Bundle;

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
    private Object mPreference;

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
                    if (getEarPhoneState()) {
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
                    if (getEarPhoneState()) {
                        returnNull();
                        logI(TAG, "Don't set misound mode, in earphone mode!");
                    }
                }
            });

            Class<?> activityClass = mDexKit.findClass(FindClass.create()
                    .matcher(ClassMatcher.create().usingStrings("supports spatial audio 3.0 "))
            ).singleOrNull().getInstance(classLoader);
            Method create = activityClass.getDeclaredMethod("onCreatePreferences", Bundle.class, String.class);
            Method onResume = activityClass.getDeclaredMethod("onResume");
            Field effectSelectionField = mDexKit.findField(FindField.create()
                    .matcher(FieldMatcher.create()
                            .declaredClass(activityClass)
                            .type(findClass("miuix.preference.DropDownPreference").get())
                            .addReadMethod(MethodMatcher.create()
                                    .declaredClass(activityClass)
                                    .usingStrings("updateEffectSelectionPreference(): set as ")
                            )
                    )).singleOrNull().getFieldInstance(classLoader);

            Class<?> preferenceCategoryClass = findClass("miuix.preference.PreferenceCategory").get();
            Class<?> preferenceClass = findClass("androidx.preference.Preference").get();
            hook(create, new IHook() {
                @Override
                public void after() {
                    Context context = (Context) callThisMethod("requireContext");
                    Object preferenceScreen = callThisMethod("getPreferenceScreen");
                    Object preferenceCategory = newInstance(preferenceCategoryClass, context, null);
                    callMethod(preferenceCategory, "setTitle", "AutoSEffSwitch");
                    callMethod(preferenceCategory, "setKey", "auto_effect_switch");

                    mPreference = newInstance(preferenceClass, context, null);
                    callMethod(mPreference, "setTitle", "基本信息:");
                    callMethod(mPreference, "setKey", "auto_effect_switch_pref");
                    updateAutoSEffSwitchInfo();

                    callMethod(preferenceScreen, "addPreference", preferenceCategory);
                    callMethod(preferenceCategory, "addPreference", mPreference);

                    logI(TAG, "create pref category: " + preferenceCategory);

                    effectSelectionPrefs = getField(thisObject(), effectSelectionField);
                    updateEarPhoneState();
                    updateEffectSelectionState();
                }
            });
            hook(onResume, new IHook() {
                @Override
                public void after() {
                    effectSelectionPrefs = getField(thisObject(), effectSelectionField);
                    updateEarPhoneState();
                    updateEffectSelectionState();
                    updateAutoSEffSwitchInfo();
                }
            });

            Method broadcastReceiver = mDexKit.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass(ClassMatcher.create().usingStrings("onReceive: to refreshEnable"))
                            .usingStrings("onReceive: to refreshEnable")
                    )
            ).singleOrNull().getMethodInstance(classLoader);
            hook(broadcastReceiver, new IHook() {
                @Override
                public void before() {
                    updateEffectSelectionState();
                    updateAutoSEffSwitchInfo();
                }
            });
        } catch (Throwable e) {
            logE(TAG, e);
        }
    }

    private void updateAutoSEffSwitchInfo() {
        if (mPreference == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("isSupport FW: ").append(isSupportFW()).append("\n");
        sb.append("isEarPhoneConnection: ").append(isEarPhoneConnection).append("\n");
        sb.append("\n# Effect Control Info:\n");
        sb.append("hasControlDolby: ").append(hasControlDolbyEffect()).append("\n");
        sb.append("hasControlMiSound: ").append(hasControlMiSound()).append("\n");
        sb.append("\n# Effect Enable Info: \n");
        sb.append("isEnableDolby: ").append(isEnableDolbyEffect()).append("\n");
        sb.append("isEnableMiSound: ").append(isEnableMiSound());

        callMethod(mPreference, "setSummary", sb.toString());
    }

    private void updateEffectSelectionState() {
        if (effectSelectionPrefs == null) return;
        if (getEarPhoneState()) {
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

        updateAutoSEffSwitchInfo();
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

        updateAutoSEffSwitchInfo();
    }

    @Override
    public void dumpAudioEffectState() {
        logI(TAG, "dolby state: " + isEnableDolbyEffect() + ", misound state: " + isEnableMiSound());
    }
}
