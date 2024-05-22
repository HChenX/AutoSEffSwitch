package com.hchen.autoseffswitch.misound;

import static com.hchen.hooktool.log.XposedLog.logE;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.provider.Settings;

import com.hchen.hooktool.BaseHC;
import com.hchen.hooktool.callback.IAction;
import com.hchen.hooktool.tool.ParamTool;
import com.hchen.hooktool.tool.StaticTool;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.UUID;

import de.robv.android.xposed.XposedHelpers;

public class AutoSEffSwitch extends BaseHC {
    private static final String TAG = "AutoSEffSwitch";
    private static Object miDolby = null;
    private static Object miAudio = null;
    private static String uuid = "";
    private String mode = null;
    private final DexKitBridge dexKitBridge;
    private static Class<?> AudioEffect = null;
    private static Class<?> MiSound = null;

    public AutoSEffSwitch(DexKitBridge dexKitBridge) {
        this.dexKitBridge = dexKitBridge;
    }

    enum Enum {
        MiSoundApplication, AudioEffect, MiSound
    }

    @Override
    public void init() {
        AudioEffect = hcHook.findClass(Enum.AudioEffect, "android.media.audiofx.AudioEffect",
                ClassLoader.getSystemClassLoader()).get();
        MiSound = hcHook.findClass(Enum.MiSound, "android.media.audiofx.MiSound",
                ClassLoader.getSystemClassLoader()).get();

        dexkit(dexKitBridge);

        hcHook.findClass(Enum.MiSoundApplication,
                        "com.miui.misound.MiSoundApplication")
                .getMethod("onCreate").hook(new IAction() {
                    @Override
                    public void after(ParamTool param, StaticTool staticTool) {
                        Application application = param.thisObject();
                        getUUID(application);
                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
                        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
                        intentFilter.addAction(AudioManager.ACTION_HEADSET_PLUG);
                        application.registerReceiver(new Listener(), intentFilter);
                        // logE(TAG, "application: " + application + " intentFilter: " + intentFilter);
                    }
                });
    }

    private void dexkit(DexKitBridge dexKitBridge) {
        ClassData classData = dexKitBridge.findClass(FindClass.create().matcher(ClassMatcher.create()
                .usingStrings("Creating a DolbyAudioEffect to global output mix!"))).singleOrNull();
        if (classData == null) {
            logE(TAG, "AudioEffect not found");
        } else {
            dexkitTool.hookMethod(classData, new IAction() {
                @Override
                public void after(ParamTool param, StaticTool staticTool) {
                    miDolby = param.thisObject();
                    // logE(TAG, "mi dolby: " + miDolby);
                }
            }, int.class, int.class);
        }

        ClassData classData1 = dexKitBridge.findClass(FindClass.create().matcher(ClassMatcher.create()
                .usingStrings("android.media.audiofx.MiSound"))).singleOrNull();
        try {
            if (classData1 == null) {
                logE(TAG, "MiSound not found");
            } else {
                FieldData fieldData = dexKitBridge.findField(FindField.create().matcher(FieldMatcher.create()
                        .declaredClass(classData1.getInstance(lpparam.classLoader)).type(Object.class)
                )).singleOrNull();
                if (fieldData == null) {
                    logE(TAG, "field not found");
                } else {
                    String name = fieldData.getFieldName();
                    dexkitTool.hookMethod(classData1, new IAction() {
                        @Override
                        public void after(ParamTool param, StaticTool staticTool) {
                            miAudio = XposedHelpers.getObjectField(param.thisObject(), name);
                            // logE(TAG, "mi audio: " + miAudio);
                        }
                    }, int.class, int.class);
                }
            }
        } catch (ClassNotFoundException e) {
            logE(TAG, e);
        }
        MethodData methodData = dexKitBridge.findMethod(FindMethod.create()
                .matcher(MethodMatcher.create()
                        .declaredClass(
                                ClassMatcher.create()
                                        .usingStrings("getEnabledEffect(): both of enabled, force return misound")
                        )
                        .usingStrings("getEnabledEffect(): both of enabled, force return misound")
                )
        ).singleOrNull();
        try {
            if (methodData == null) {
                logE(TAG, "method data is null");
            } else {
                Method method = methodData.getMethodInstance(lpparam.classLoader);
                dexkitTool.hookMethod(methodData, new IAction() {
                    @Override
                    public void before(ParamTool param, StaticTool staticTool) {
                        Context context = param.callMethod("getActivity");
                        boolean isBluetoothA2dpOn = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE)).isBluetoothA2dpOn();
                        boolean isWiredHeadsetOn = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE)).isWiredHeadsetOn();
                        if (isBluetoothA2dpOn || isWiredHeadsetOn) {
                            if (mode == null) mode = "none";
                        }
                        if (mode != null) {
                            param.setResult(mode);
                        }
                        // logE(TAG, "isBluetoothA2dpOn: " + isBluetoothA2dpOn + " isBluetoothA2dpOn: " + isBluetoothA2dpOn + " mode: " + mode);
                    }
                });

                dexkitTool.hookMethod(dexkitTool.getMethod(
                                method.getDeclaringClass(), "onPreferenceChange",
                                "androidx.preference.Preference", Object.class),
                        new IAction() {
                            @Override
                            public void before(ParamTool param, StaticTool staticTool) {
                                Object o = param.second();
                                if (o instanceof String) {
                                    if ("none".equals(o) || "dolby".equals(o) || "misound".equals(o))
                                        mode = (String) o;
                                }
                                // logE(TAG, "o: " + o);
                            }
                        });

                MethodData methodData1 = dexKitBridge.findMethod(FindMethod.create().matcher(
                        MethodMatcher.create().declaredClass(method.getDeclaringClass())
                                .usingStrings("refreshEffectSelectionEnabled(): currEffect "))).singleOrNull();
                dexkitTool.hookMethod(methodData1, new IAction() {
                    @Override
                    public void after(ParamTool param, StaticTool staticTool) {
                        mode = null;
                        // logE(TAG, "this");
                    }
                });
            }
        } catch (Throwable e) {
            logE(TAG, e);
        }
    }

    private void getUUID(Context context) {
        String result = Settings.Global.getString(context.getContentResolver(), "aseff_uuid");
        if (result == null) return;
        uuid = result;
    }

    private static String effectImplementer(Context context) {
        return Settings.Global.getString(context.getContentResolver(), "effect_implementer");
    }

    private static Object getAudioEffect() {
        if (AudioEffect == null) return null;
        // Class<?> DolbyAudioEffectHelper = findClassIfExists("com.android.server.audio.dolbyeffect.DolbyEffectController$DolbyAudioEffectHelper",
        //         ClassLoader.getSystemClassLoader());
        // logE(TAG, "DolbyAudioEffectHelper: " + DolbyAudioEffectHelper);
        // UUID dolby = (UUID) XposedHelpers.getStaticObjectField(
        //         DolbyAudioEffectHelper, "EFFECT_TYPE_DOLBY_AUDIO_PROCESSING");
        UUID EFFECT_TYPE_NULL = expandTool.getStaticField(AudioEffect, "EFFECT_TYPE_NULL");
        UUID dolby;
        if (uuid.isEmpty()) {
            dolby = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa");
        } else {
            dolby = UUID.fromString(uuid);
        }
        return expandTool.newInstance(AudioEffect, new Object[]{EFFECT_TYPE_NULL, dolby, 0, 0});
    }

    private static Object getMiSound() {
        if (MiSound == null) return null;
        return expandTool.newInstance(MiSound, new Object[]{1, 0});
    }

    private static boolean hasControl(Object o) {
        return Boolean.TRUE.equals(expandTool.callMethod(o, "hasControl"));
    }

    private static boolean isEnable(Object o) {
        return Boolean.TRUE.equals(expandTool.callMethod(o, "getEnabled"));
    }

    private static void setEnable(Object o, boolean value) {
        expandTool.callMethod(o, "setEnabled", value);
    }

    public static class Listener extends BroadcastReceiver {
        private static Object AudioEffect = null;
        private static Object MiSound = null;
        private static boolean lastDolby;
        private static boolean lastMiui;

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        on(context);
                    }
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        over(context);
                    }
                    case AudioManager.ACTION_HEADSET_PLUG -> {
                        init();
                        if (intent.hasExtra("state")) {
                            int state = intent.getIntExtra("state", 0);
                            if (state == 1) {
                                on(context);
                            } else if (state == 0) {
                                over(context);
                            }
                        }
                    }
                }
            }
        }

        private void on(Context context) {
            init();
            lastDolby = setAudio(AudioEffect, miDolby);
            lastMiui = setAudio(MiSound, miAudio);
            String implementer = effectImplementer(context);
            // logE(TAG, "A: " + AudioEffect + " d: " + miDolby + " M: " + MiSound + " a: " + miAudio
            //         + " co: " + hasControl(AudioEffect) + " co1: " + hasControl(MiSound) +
            //         " laD: " + lastDolby + " laM: " + lastMiui + " im: " + implementer);
            if (implementer != null) {
                if ("dolby".equals(implementer)) {
                    lastDolby = true;
                    lastMiui = false;
                } else if ("misound".equals(implementer)) {
                    lastDolby = false;
                    lastMiui = true;
                }
            }
            // logE(TAG, "last dolby: " + lastDolby + " last miui: " + lastMiui);
            refresh(context, false, false);
        }

        private void refresh(Context context, boolean dolby, boolean miui) {
            Intent intent = new Intent();
            intent.setAction("miui.intent.action.ACTION_AUDIO_EFFECT_REFRESH");
            intent.putExtra("dolby_active", dolby);
            intent.putExtra("misound_active", miui);
            context.sendBroadcast(intent);
        }

        private void over(Context context) {
            init();
            recoveryAudio(AudioEffect, miDolby, lastDolby);
            recoveryAudio(MiSound, miAudio, lastMiui);
            refresh(context, lastDolby, lastMiui);
            // logE(TAG, "A: " + AudioEffect + " d: " + miDolby + " M: " + MiSound + " a: " + miAudio
            //         + " co: " + hasControl(AudioEffect) + " co1: " + hasControl(MiSound) +
            //         " laD: " + lastDolby + " laM: " + lastMiui);
        }

        private static boolean setAudio(Object audio, Object otherAudio) {
            boolean last;
            if (audio != null) {
                if (hasControl(audio)) {
                    last = isEnable(audio);
                    setEnable(audio, false);
                    return last;
                } else if (otherAudio != null) {
                    if (hasControl(otherAudio)) {
                        last = isEnable(otherAudio);
                        setEnable(otherAudio, false);
                        return last;
                    }
                }
            }
            return false;
        }

        private static void recoveryAudio(Object audio, Object otherAudio, boolean last) {
            if (audio != null) {
                if (last != isEnable(audio)) {
                    if (hasControl(audio)) {
                        setEnable(audio, last);
                    } else if (otherAudio != null) {
                        if (hasControl(otherAudio)) {
                            setEnable(otherAudio, last);
                        }
                    }
                }
            }
        }

        private void init() {
            if (AudioEffect == null) {
                AudioEffect = getAudioEffect();
            }
            if (MiSound == null) {
                MiSound = getMiSound();
            }
        }
    }
}
