package com.hchen.autoseffswitch.hook.misound.control;

import static com.hchen.autoseffswitch.data.EffectItem.mEffectArray;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.getEarPhoneStateFinal;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.isSupportFW;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.mDexKit;
import static com.hchen.hooktool.BaseHC.classLoader;
import static com.hchen.hooktool.log.XposedLog.logE;
import static com.hchen.hooktool.log.XposedLog.logI;
import static com.hchen.hooktool.tool.CoreTool.callMethod;
import static com.hchen.hooktool.tool.CoreTool.findClass;
import static com.hchen.hooktool.tool.CoreTool.getField;
import static com.hchen.hooktool.tool.CoreTool.hook;
import static com.hchen.hooktool.tool.CoreTool.hookAll;
import static com.hchen.hooktool.tool.CoreTool.hookMethod;
import static com.hchen.hooktool.tool.CoreTool.newInstance;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;

import com.hchen.autoseffswitch.IEffectInfo;
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
import java.util.Arrays;
import java.util.Map;

public class NewFWAudioEffectControl {
    public static final String TAG = "NewFWAudioEffectControl";
    private Object mEffectSelectionPrefs;
    private Object mPreference;
    public IEffectInfo mIEffectInfo;

    public void init() {
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
                    if (getEarPhoneStateFinal()) {
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
                    if (getEarPhoneStateFinal())
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

                    mEffectSelectionPrefs = getField(thisObject(), prefsField);
                    updateEffectSelectionState();
                }
            });
            hookAll(new Member[]{refresh, onResume}, new IHook() {
                @Override
                public void after() {
                    mEffectSelectionPrefs = getField(thisObject(), prefsField);
                    updateEffectSelectionState();
                    updateAutoSEffSwitchInfo();
                }
            });
        } catch (Throwable e) {
            logE(TAG, e);
        }
    }

    public void updateEffectSelectionState() {
        if (mEffectSelectionPrefs == null) return;
        if (getEarPhoneStateFinal()) {
            callMethod(mEffectSelectionPrefs, "setEnabled", false);
            logI(TAG, "Disable effect selection: " + mEffectSelectionPrefs);
        } else
            callMethod(mEffectSelectionPrefs, "setEnabled", true);
    }

    private void updateAutoSEffSwitchInfo() {
        if (mPreference == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("isSupport FW: ").append(isSupportFW()).append("\n");
        sb.append("isEarPhoneConnection: ").append(getEarPhoneStateFinal()).append("\n");

        if (mIEffectInfo != null) {
            try {
                Map<String, String> map = mIEffectInfo.getEffectSupportMap();
                sb.append("\n# Effect Support Info:\n");
                Arrays.stream(mEffectArray).forEach(s -> {
                    String state = map.get(s);
                    sb.append("isSupport").append(s.substring(0, 1).toUpperCase())
                            .append(s.substring(1).toLowerCase())
                            .append(": ").append(state).append("\n");
                });
            } catch (RemoteException e) {
                logE(TAG, e);
            }

            try {
                Map<String, String> map = mIEffectInfo.getEffectAvailableMap();
                sb.append("\n# Effect Available Info:\n");
                Arrays.stream(mEffectArray).forEach(s -> {
                    String state = map.get(s);
                    sb.append("isAvailable").append(s.substring(0, 1).toUpperCase())
                            .append(s.substring(1).toLowerCase())
                            .append(": ").append(state).append("\n");
                });
            } catch (RemoteException e) {
                logE(TAG, e);
            }

            try {
                Map<String, String> map = mIEffectInfo.getEffectActiveMap();
                logI(TAG, "Effect Active Info: " + map);
                sb.append("\n# Effect Active Info:\n");
                Arrays.stream(mEffectArray).forEach(s -> {
                    String state = map.get(s);
                    sb.append("isActive").append(s.substring(0, 1).toUpperCase())
                            .append(s.substring(1).toLowerCase())
                            .append(": ").append(state).append("\n");
                });
            } catch (RemoteException e) {
                logE(TAG, e);
            }
        }

        callMethod(mPreference, "setSummary", sb.toString());
    }
}
