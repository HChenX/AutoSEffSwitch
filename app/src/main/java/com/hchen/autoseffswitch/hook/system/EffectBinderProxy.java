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
package com.hchen.autoseffswitch.hook.system;

import static com.hchen.autoseffswitch.hook.system.AutoEffectSwitchForSystem.mEffectInfoService;
import static com.hchen.hooktool.log.XposedLog.logI;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import com.hchen.hooktool.BaseHC;
import com.hchen.hooktool.hook.IHook;

/**
 * 代理 AIDL
 *
 * @author 焕晨HChen
 */
public class EffectBinderProxy extends BaseHC {

    @Override
    protected void init() {
        hookMethod("com.android.server.am.ActivityManagerService",
                "registerReceiverWithFeature",
                "android.app.IApplicationThread", String.class, String.class, String.class,
                "android.content.IIntentReceiver", IntentFilter.class, String.class, int.class, int.class,
                new IHook() {
                    @Override
                    public void after() {
                        Intent intent = (Intent) getResult();
                        String callerPackage = (String) getArgs(1);
                        if (intent == null) return;
                        if (!"com.miui.misound".equals(callerPackage) || !"com.android.systemui".equals(callerPackage)) return;
                        logI(TAG, "caller package: " + callerPackage + " mEffectInfoService: " + mEffectInfoService);

                        Bundle bundle = new Bundle();
                        if (mEffectInfoService == null) return;
                        bundle.putBinder("effect_info", mEffectInfoService);
                        intent.putExtra("effect_info", bundle);
                        setResult(intent);
                    }
                }
        );
    }
}
