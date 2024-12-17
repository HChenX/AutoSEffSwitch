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
import static com.hchen.hooktool.tool.CoreTool.getStaticField;
import static com.hchen.hooktool.tool.CoreTool.hook;
import static com.hchen.hooktool.tool.CoreTool.hookAll;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;

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
import java.util.ArrayList;
import java.util.List;

/**
 * 较新版本的切换逻辑
 *
 * @author 焕晨HChen
 */
public class FWAudioEffectControl implements IControl {
    public Class<?> mAudioEffectCenter = null;
    public Object mAudioEffectCenterInstance = null;
    private String EFFECT_DOLBY = "";
    private String EFFECT_MISOUND = "";
    private String EFFECT_NONE = "";
    private String EFFECT_SPATIAL_AUDIO = "";
    private String EFFECT_SURROUND = "";
    private Object effectSelectionPrefs;
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
                    if (isEarPhoneConnection) {
                        returnNull();
                        logI(TAG, "Dont set dolby or misound, in earphone mode!!");
                    }
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
                    if (isEarPhoneConnection)
                        returnFalse();
                }
            });

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
            ArrayList<Method> methods = new ArrayList<>();
            methods.add(refresh);
            methods.add(create);
            methods.add(onResume);

            hookAll(methods, new IHook() {
                @Override
                public void after() {
                    updateEarPhoneState();

                    effectSelectionPrefs = getField(thisObject(), prefsField);
                    updateEffectSelectionState();
                }
            });
        } catch (Throwable e) {
            logE(TAG, e);
        }

        logI(TAG, "D: " + EFFECT_DOLBY + ", M: " + EFFECT_MISOUND + ", N: " + EFFECT_NONE + ", S: " + EFFECT_SPATIAL_AUDIO + ", SU: " + EFFECT_SURROUND);
    }

    private void updateEffectSelectionState() {
        if (effectSelectionPrefs == null) return;
        if (isEarPhoneConnection) {
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
            if (isEffectActive(s))
                mLastEffectStateList.add(s);
        });
    }

    @Override
    public void setEffectToNone(Context context) {
        setEffectActive(EFFECT_NONE, true);
        Settings.Global.putString(context.getContentResolver(), "effect_implementer", EFFECT_NONE);
    }

    @Override
    public void resetAudioEffect() {
        if (mLastEffectStateList.isEmpty()) {
            if (isEffectSupported(EFFECT_DOLBY) && isEffectAvailable(EFFECT_DOLBY))
                setEffectActive(EFFECT_DOLBY, true);
            else if (isEffectSupported(EFFECT_MISOUND) && isEffectAvailable(EFFECT_MISOUND))
                setEffectActive(EFFECT_MISOUND, true);

            if (isEffectSupported(EFFECT_SPATIAL_AUDIO) && isEffectAvailable(EFFECT_SPATIAL_AUDIO))
                setEffectActive(EFFECT_SPATIAL_AUDIO, true);
            if (isEffectSupported(EFFECT_SURROUND) && isEffectAvailable(EFFECT_SURROUND))
                setEffectActive(EFFECT_SURROUND, true);
        } else
            mLastEffectStateList.forEach(s -> setEffectActive(s, true));
        mLastEffectStateList.clear();
    }

    @Override
    public void dumpAudioEffectState() {
        StringBuilder builder = new StringBuilder();
        mEffectList.forEach(s ->
                builder.append(s).append(": ").append(isEffectActive(s)).append(", "));
        logI(TAG, builder.toString());
    }
}
