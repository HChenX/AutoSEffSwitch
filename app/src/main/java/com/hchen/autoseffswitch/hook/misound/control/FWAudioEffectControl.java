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
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.isBroadcastReceiverCanUse;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.isEarPhoneConnection;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.isSupportFW;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.mDexKit;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.shouldFixXiaoMiShit;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.updateEarPhoneState;
import static com.hchen.hooktool.BaseHC.classLoader;
import static com.hchen.hooktool.log.XposedLog.logE;
import static com.hchen.hooktool.log.XposedLog.logI;
import static com.hchen.hooktool.tool.CoreTool.callMethod;
import static com.hchen.hooktool.tool.CoreTool.findClass;
import static com.hchen.hooktool.tool.CoreTool.getField;
import static com.hchen.hooktool.tool.CoreTool.getStaticField;
import static com.hchen.hooktool.tool.CoreTool.hook;
import static com.hchen.hooktool.tool.CoreTool.hookAll;
import static com.hchen.hooktool.tool.CoreTool.newInstance;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.Nullable;

import com.hchen.autoseffswitch.hook.misound.backups.BackupsUtils;
import com.hchen.autoseffswitch.hook.misound.callback.IControl;
import com.hchen.hooktool.hook.IHook;

import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 较新版本的切换逻辑
 *
 * @author 焕晨HChen
 */
public class FWAudioEffectControl implements IControl {
    private Context mContext;
    @Nullable
    private BackupsUtils mBackupsUtils;
    public Class<?> mAudioEffectCenter = null;
    public Object mAudioEffectCenterInstance = null;
    private String EFFECT_DOLBY = "";
    private String EFFECT_MISOUND = "";
    private String EFFECT_NONE = "";
    private String EFFECT_SPATIAL_AUDIO = "";
    private String EFFECT_SURROUND = "";
    private Object effectSelectionPrefs;
    private Object mPreference;
    private final List<String> mEffectList = new ArrayList<>();
    private final List<String> mLastEffectStateList = new ArrayList<>();

    public void init() {
        mAudioEffectCenter = findClass("android.media.audiofx.AudioEffectCenter").get();
        if (mAudioEffectCenter == null) return;

        EFFECT_DOLBY = (String) getStaticField(mAudioEffectCenter, "EFFECT_DOLBY");
        EFFECT_MISOUND = (String) getStaticField(mAudioEffectCenter, "EFFECT_MISOUND");
        EFFECT_NONE = (String) getStaticField(mAudioEffectCenter, "EFFECT_NONE");
        EFFECT_SPATIAL_AUDIO = (String) getStaticField(mAudioEffectCenter, "EFFECT_SPATIAL_AUDIO");
        EFFECT_SURROUND = (String) getStaticField(mAudioEffectCenter, "EFFECT_SURROUND");

        mEffectList.add(EFFECT_DOLBY);
        mEffectList.add(EFFECT_MISOUND);
        mEffectList.add(EFFECT_NONE);
        mEffectList.add(EFFECT_SPATIAL_AUDIO);
        mEffectList.add(EFFECT_SURROUND);

        try {
            Method audioEffectCenterEnable = mDexKit.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass(ClassMatcher.create().usingStrings("setEffectActive IllegalAccessException"))
                            .usingStrings("setEffectActive IllegalAccessException")
                    )
            ).singleOrNull().getMethodInstance(classLoader);
            hook(audioEffectCenterEnable, new IHook() {
                @Override
                public void before() {
                    if (getEarPhoneState()) {
                        returnNull();
                        logI(TAG, "Dont set dolby or misound, in earphone mode!!");
                    }
                    logI(TAG, "im set dolby or misound! ear: " + getEarPhoneState());
                }
            });

            Method click = mDexKit.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass(ClassMatcher.create().usingStrings("click vSoundEffectSelection "))
                            .usingStrings("click vSoundEffectSelection ")
                    )
            ).singleOrNull().getMethodInstance(classLoader);
            hook(click, new IHook() {
                @Override
                public void before() {
                    if (getEarPhoneState())
                        returnFalse();
                }
            });

            Method btChange = mDexKit.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass(findClass("com.miui.misound.soundid.receiver.BTChangeStaticBroadCastReceiver").get())
                            .usingStrings("changeCurrentSoundId: to set new gain of ")
                    )
            ).singleOrNull().getMethodInstance(classLoader);
            hook(btChange, new IHook() {
                        @Override
                        public void before() {
                            logI(TAG, "isBroadcastReceiverCanUse: " + isBroadcastReceiverCanUse);
                            if (isBroadcastReceiverCanUse) return;
                            Object bluetoothDevice = getArgs(1);
                            if (bluetoothDevice != null) {
                                isEarPhoneConnection = true;
                                shouldFixXiaoMiShit = true;
                                updateLastEffectState();
                                setEffectToNone(mContext);
                            } else {
                                isEarPhoneConnection = false;
                                shouldFixXiaoMiShit = true;
                                resetAudioEffect();
                            }
                        }
                    }
            );

            Class<?> activityClass = mDexKit.findClass(FindClass.create()
                    .matcher(ClassMatcher.create().usingStrings("refreshOnEffectChangeBroadcast AV Dolby: "))
            ).singleOrNull().getInstance(classLoader);
            Method refresh = mDexKit.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass(activityClass)
                            .usingStrings("refreshOnEffectChangeBroadcast AV Dolby: ")
                    )
            ).singleOrNull().getMethodInstance(classLoader);
            Method create = activityClass.getDeclaredMethod("onCreatePreferences", Bundle.class, String.class);
            Method onResume = activityClass.getDeclaredMethod("onResume");
            Field prefsField = mDexKit.findField(FindField.create()
                    .matcher(FieldMatcher.create()
                            .declaredClass(activityClass)
                            .type(findClass("miuix.preference.DropDownPreference").get())
                    )
            ).singleOrNull().getFieldInstance(classLoader);

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

                    effectSelectionPrefs = getField(thisObject(), prefsField);
                    updateEarPhoneState();
                    updateEffectSelectionState();
                }
            });
            hookAll(new Member[]{refresh, onResume}, new IHook() {
                @Override
                public void after() {
                    effectSelectionPrefs = getField(thisObject(), prefsField);
                    updateEarPhoneState();
                    updateEffectSelectionState();
                    updateAutoSEffSwitchInfo();
                }
            });
        } catch (Throwable e) {
            logE(TAG, e);
        }

        logI(TAG, "D: " + EFFECT_DOLBY + ", M: " + EFFECT_MISOUND + ", N: " + EFFECT_NONE + ", S: " + EFFECT_SPATIAL_AUDIO + ", SU: " + EFFECT_SURROUND);
    }

    public void setContext(Context context) {
        mContext = context;
    }

    public void setBackups(BackupsUtils backupsUtils) {
        mBackupsUtils = backupsUtils;
    }

    private void updateAutoSEffSwitchInfo() {
        if (mPreference == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("isSupport FW: ").append(isSupportFW()).append("\n");
        sb.append("isEarPhoneConnection: ").append(isEarPhoneConnection).append("\n");

        sb.append("\n# Effect Support Info:\n");
        mEffectList.forEach(s -> sb.append("isSupport").append(s.substring(0, 1).toUpperCase())
                .append(s.substring(1).toLowerCase()).append(": ").append(isEffectSupported(s)).append("\n"));

        sb.append("\n# Effect Available Info:\n");
        mEffectList.forEach(s -> sb.append("isAvailable").append(s.substring(0, 1).toUpperCase())
                .append(s.substring(1).toLowerCase()).append(": ").append(isEffectAvailable(s)).append("\n"));

        sb.append("\n# Effect Active Info:\n");
        mEffectList.forEach(s -> sb.append("isActive").append(s.substring(0, 1).toUpperCase())
                .append(s.substring(1).toLowerCase()).append(": ").append(isEffectActive(s)).append("\n"));

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

    private boolean isEffectSupported(String effect) {
        if (mAudioEffectCenterInstance != null)
            return (boolean) callMethod(mAudioEffectCenterInstance, "isEffectSupported", effect);
        return false;
    }

    private boolean isEffectAvailable(String effect) {
        if (mAudioEffectCenterInstance != null)
            return (boolean) callMethod(mAudioEffectCenterInstance, "isEffectAvailable", effect);
        return false;
    }

    private boolean isEffectActive(String effect) {
        if (mAudioEffectCenterInstance != null)
            return (boolean) callMethod(mAudioEffectCenterInstance, "isEffectActive", effect);
        return false;
    }

    private void setEffectActive(String effect, boolean active) {
        if (mAudioEffectCenterInstance != null)
            callMethod(mAudioEffectCenterInstance, "setEffectActive", effect, active);
    }

    @Override
    public void updateLastEffectState() {
        if (mEffectList.isEmpty()) return;
        mLastEffectStateList.clear();
        mEffectList.forEach(s -> {
            if (isEffectActive(s)) {
                mLastEffectStateList.add(s);
                if (mBackupsUtils != null && mBackupsUtils.supportBackups())
                    mBackupsUtils.saveAnyState(s, true);
            }
        });
    }

    @Override
    public void setEffectToNone(Context context) {
        setEffectActive(EFFECT_NONE, true);
        if (context != null)
            Settings.Global.putString(context.getContentResolver(), "effect_implementer", EFFECT_NONE);

        updateEffectSelectionState();
        updateAutoSEffSwitchInfo();
    }

    @Override
    public void resetAudioEffect() {
        if (mLastEffectStateList.isEmpty()) {
            if (mBackupsUtils != null && mBackupsUtils.supportBackups())
                mBackupsUtils.getAllState().forEach((BiConsumer<String, Object>) (s, object) -> {
                    if (mEffectList.contains(s) && object instanceof Boolean b && b)
                        mLastEffectStateList.add(s);
                });
        }

        if (mLastEffectStateList.isEmpty()) {
            if (isEffectSupported(EFFECT_DOLBY) && isEffectAvailable(EFFECT_DOLBY)) {
                setEffectActive(EFFECT_DOLBY, true);
            } else if (isEffectSupported(EFFECT_MISOUND) && isEffectAvailable(EFFECT_MISOUND)) {
                setEffectActive(EFFECT_MISOUND, true);
            }

            if (isEffectSupported(EFFECT_SPATIAL_AUDIO) && isEffectAvailable(EFFECT_SPATIAL_AUDIO))
                setEffectActive(EFFECT_SPATIAL_AUDIO, true);
            if (isEffectSupported(EFFECT_SURROUND) && isEffectAvailable(EFFECT_SURROUND))
                setEffectActive(EFFECT_SURROUND, true);
        } else
            mLastEffectStateList.forEach(s -> setEffectActive(s, true));
        mLastEffectStateList.clear();

        updateEffectSelectionState();
        updateAutoSEffSwitchInfo();
        if (mBackupsUtils != null && mBackupsUtils.supportBackups())
            mBackupsUtils.clearAll();
    }

    @Override
    public void dumpAudioEffectState() {
        StringBuilder builder = new StringBuilder();
        mEffectList.forEach(s ->
                builder.append(s).append(": ").append(isEffectActive(s)).append(", "));
        logI(TAG, builder.toString());
    }
}
