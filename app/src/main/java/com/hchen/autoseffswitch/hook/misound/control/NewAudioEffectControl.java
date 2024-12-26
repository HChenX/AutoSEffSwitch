package com.hchen.autoseffswitch.hook.misound.control;

import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.TAG;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.getEarPhoneState;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.isEarPhoneConnection;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.isSupportFW;
import static com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch.mDexKit;
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
import android.os.RemoteException;

import com.hchen.autoseffswitch.data.EffectItem;
import com.hchen.autoseffswitch.hook.system.binder.EffectInfoService;
import com.hchen.hooktool.hook.IHook;

import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class NewAudioEffectControl {
    private Object mPreference;
    private Object mEffectSelectionPrefs;
    public EffectInfoService mEffectInfoService;

    public void init() {
        try {
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

                    mEffectSelectionPrefs = getField(thisObject(), effectSelectionField);
                    updateEffectSelectionState();
                }
            });
            hook(onResume, new IHook() {
                @Override
                public void after() {
                    mEffectSelectionPrefs = getField(thisObject(), effectSelectionField);
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

    private void updateEffectSelectionState() {
        if (mEffectSelectionPrefs == null) return;
        if (getEarPhoneState()) {
            callMethod(mEffectSelectionPrefs, "setEnabled", false);
            logI(TAG, "Disable effect selection: " + mEffectSelectionPrefs);
        } else
            callMethod(mEffectSelectionPrefs, "setEnabled", true);
    }

    private void updateAutoSEffSwitchInfo() {
        if (mPreference == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("isSupport FW: ").append(isSupportFW()).append("\n");
        sb.append("isEarPhoneConnection: ").append(isEarPhoneConnection).append("\n");
        if (mEffectInfoService != null) {
            try {
                Map<String, String> map = mEffectInfoService.getEffectEnabledMap();
                sb.append("\n# Effect Enable Info: \n");
                sb.append("isEnableDolby: ").append(map.get(EffectItem.EFFECT_DOLBY)).append("\n");
                sb.append("isEnableMiSound: ").append(map.get(EffectItem.EFFECT_MISOUND)).append("\n");
                sb.append("isEnableSpatializer: ").append(map.get(EffectItem.EFFECT_SPATIAL_AUDIO)).append("\n");
                sb.append("isEnable3dSurround: ").append(map.get(EffectItem.EFFECT_SURROUND)).append("\n");
            } catch (RemoteException e) {
                logE(TAG, e);
            }
        }

        callMethod(mPreference, "setSummary", sb.toString());
    }
}
