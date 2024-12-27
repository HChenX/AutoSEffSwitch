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
package com.hchen.autoseffswitch;

import com.hchen.autoseffswitch.config.ModuleConfig;
import com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch;
import com.hchen.autoseffswitch.hook.system.AutoEffectSwitchForSystem;
import com.hchen.autoseffswitch.hook.system.EffectBinderProxy;
import com.hchen.autoseffswitch.hook.systemui.AutoSEffSwitchForSystemUi;
import com.hchen.hooktool.HCEntrance;
import com.hchen.hooktool.HCInit;

import org.luckypray.dexkit.DexKitBridge;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook 入口
 *
 * @author 焕晨HChen
 */
public class XposedInit extends HCEntrance {
    @Override
    public HCInit.BasicData initHC(HCInit.BasicData basicData) {
        return basicData.setModulePackageName("com.hchen.autoseffswitch")
                .setTag("AutoSEffSwitch")
                .setLogLevel(HCInit.LOG_D)
                .initLogExpand(new String[]{
                        "com.hchen.autoseffswitch.hook"
                });
    }

    @Override
    public void onLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        switch (lpparam.packageName) {
            case "android" -> {
                if (ModuleConfig.useNewVersion) {
                    HCInit.initLoadPackageParam(lpparam);
                    new EffectBinderProxy().onLoadPackage();
                    new AutoEffectSwitchForSystem().onLoadPackage();
                }
            }
            case "com.android.systemui" -> {
                HCInit.initLoadPackageParam(lpparam);
                new AutoSEffSwitchForSystemUi().onLoadPackage();
            }
            case "com.miui.misound" -> {
                HCInit.initLoadPackageParam(lpparam);
                String hostDir = lpparam.appInfo.sourceDir;
                System.loadLibrary("dexkit");
                NewAutoSEffSwitch.mDexKit = DexKitBridge.create(hostDir);
                new NewAutoSEffSwitch().onApplicationCreate().onLoadPackage();
                NewAutoSEffSwitch.mDexKit.close();
            }
        }
    }
}
