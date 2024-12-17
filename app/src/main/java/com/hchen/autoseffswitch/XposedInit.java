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
package com.hchen.autoseffswitch;

import com.hchen.autoseffswitch.hook.misound.NewAutoSEffSwitch;
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
        if ("com.miui.misound".equals(lpparam.packageName) || "android".equals(lpparam.packageName)) {
            HCInit.initLoadPackageParam(lpparam);
            init(lpparam.packageName, lpparam);
        }
    }

    public void init(String pkg, XC_LoadPackage.LoadPackageParam loadPackageParam) {
        switch (pkg) {
            /*case "android" -> {
                new CmdHelper().onLoadPackage();
            }*/
            case "com.miui.misound" -> {
                String hostDir = loadPackageParam.appInfo.sourceDir;
                System.loadLibrary("dexkit");
                NewAutoSEffSwitch.mDexKit = DexKitBridge.create(hostDir);
                new NewAutoSEffSwitch().onApplicationCreate().onLoadPackage();
                NewAutoSEffSwitch.mDexKit.close();
            }
        }
    }
}
