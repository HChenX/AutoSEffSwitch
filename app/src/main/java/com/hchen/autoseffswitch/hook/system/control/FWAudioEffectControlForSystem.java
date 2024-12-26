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
import static com.hchen.autoseffswitch.data.EffectItem.EFFECT_MISOUND;
import static com.hchen.autoseffswitch.data.EffectItem.EFFECT_NONE;
import static com.hchen.autoseffswitch.data.EffectItem.EFFECT_SPATIAL_AUDIO;
import static com.hchen.autoseffswitch.data.EffectItem.EFFECT_SURROUND;
import static com.hchen.autoseffswitch.hook.system.AutoEffectSwitchForSystem.getEarPhoneStateFinal;
import static com.hchen.hooktool.log.XposedLog.logI;
import static com.hchen.hooktool.tool.CoreTool.callMethod;
import static com.hchen.hooktool.tool.CoreTool.getField;
import static com.hchen.hooktool.tool.CoreTool.hookMethod;

import android.content.Context;
import android.provider.Settings;

import com.hchen.autoseffswitch.hook.system.callback.IControl;
import com.hchen.hooktool.hook.IHook;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * 从系统层面控制音效
 *
 * @author 焕晨HChen
 */
public class FWAudioEffectControlForSystem extends BaseEffectControl implements IControl {
    public static final String TAG = "FWAudioEffectControlForSystem";
    private Object mPresenter = null;
    private Object mCenter = null;
    public static final String[] mEffectArray = new String[]{
            EFFECT_DOLBY, EFFECT_MISOUND, EFFECT_NONE, EFFECT_SPATIAL_AUDIO, EFFECT_SURROUND
    };
    public static final ArrayList<String> mLastEffectList = new ArrayList<>();

    public void init() {
        hookMethod("android.media.audiofx.AudioEffectCenter",
                "getInstance",
                Context.class, new IHook() {
                    @Override
                    public void after() {
                        mCenter = getResult();
                        if (mCenter == null) return;
                        mPresenter = getField(mCenter, "mPresenter");
                    }
                }
        );

        hookMethod("android.media.audiofx.AudioEffectCenter",
                "setEffectActive",
                String.class, boolean.class,
                new IHook() {
                    @Override
                    public void before() {
                        observeCall();
                        if (getEarPhoneStateFinal()) {
                            logI(TAG, "earphone is connection, skip set effect: " + getArgs(0) + "!!");
                            returnNull();
                        }
                    }
                }
        );
    }

    private boolean isEffectSupported(String effect) {
        if (mCenter != null)
            return (boolean) callMethod(mCenter, "isEffectSupported", effect);
        return false;
    }

    private boolean isEffectAvailable(String effect) {
        if (mCenter != null)
            return (boolean) callMethod(mCenter, "isEffectAvailable", effect);
        return false;
    }

    private boolean isEffectActive(String effect) {
        if (mCenter != null)
            return (boolean) callMethod(mCenter, "isEffectActive", effect);
        return false;
    }

    private void setEffectActive(String effect, boolean active) {
        if (mPresenter != null) {
            switch (effect) {
                case EFFECT_DOLBY -> {
                    if (active)
                        callMethod(mPresenter, "setDolbyActive");
                }
                case EFFECT_MISOUND -> {
                    if (active)
                        callMethod(mPresenter, "setMiSoundActive");
                }
                case EFFECT_SPATIAL_AUDIO -> {
                    callMethod(mPresenter, "setSpatialAudioActive", active);
                }
                case EFFECT_SURROUND -> {
                    callMethod(mPresenter, "setSurroundActive", active);
                }
            }
        }
    }

    @Override
    void updateEffectMap() {
        mEffectSupportMap.clear();
        Arrays.stream(mEffectArray).forEach(s ->
                mEffectSupportMap.put(s, String.valueOf(isEffectSupported(s))));

        mEffectAvailableMap.clear();
        Arrays.stream(mEffectArray).forEach(s ->
                mEffectAvailableMap.put(s, String.valueOf(isEffectAvailable(s))));

        mEffectActiveMap.clear();
        Arrays.stream(mEffectArray).forEach(s ->
                mEffectActiveMap.put(s, String.valueOf(isEffectActive(s))));

        logI(TAG, "updateEffectMap: mCenter: " + mCenter + ", mPresenter: " + mPresenter);
    }

    @Override
    public void updateLastEffectState() {
        mLastEffectList.clear();
        Arrays.stream(mEffectArray).forEach(s -> {
            if (isEffectActive(s))
                mLastEffectList.add(s);
        });
    }

    @Override
    public void setEffectToNone(Context context) {
        if (mPresenter == null) return;
        callMethod(mPresenter, "setEffectDeactivate");

        if (context != null)
            Settings.Global.putString(context.getContentResolver(), "effect_implementer", EFFECT_NONE);
    }

    @Override
    public void resetAudioEffect() {
        if (mLastEffectList.isEmpty()) {
            if (isEffectSupported(EFFECT_DOLBY) && isEffectAvailable(EFFECT_DOLBY)) {
                setEffectActive(EFFECT_DOLBY, true);
            } else if (isEffectSupported(EFFECT_MISOUND) && isEffectAvailable(EFFECT_MISOUND)) {
                setEffectActive(EFFECT_MISOUND, true);
            }

            if (isEffectSupported(EFFECT_SPATIAL_AUDIO) && isEffectAvailable(EFFECT_SPATIAL_AUDIO))
                setEffectActive(EFFECT_SPATIAL_AUDIO, true);
            if (isEffectSupported(EFFECT_SURROUND) && isEffectAvailable(EFFECT_SURROUND))
                setEffectActive(EFFECT_SURROUND, true);
            return;
        }
        mLastEffectList.forEach(s -> setEffectActive(s, true));
        mLastEffectList.clear();
    }

    @Override
    public void dumpAudioEffectState() {
        StringBuilder builder = new StringBuilder();
        Arrays.stream(mEffectArray).forEach(s ->
                builder.append(s).append(": ").append(isEffectActive(s)).append(", "));
        logI(TAG, builder.toString());
    }
}
