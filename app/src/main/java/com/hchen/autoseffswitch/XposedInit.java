package com.hchen.autoseffswitch;

import com.hchen.autoseffswitch.misound.AutoSEffSwitch;
import com.hchen.autoseffswitch.system.CmdHelper;
import com.hchen.hooktool.HCInit;

import org.luckypray.dexkit.DexKitBridge;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook 入口
 *
 * @author 焕晨HChen
 */
public class XposedInit implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("com.miui.misound".equals(lpparam.packageName) || "android".equals(lpparam.packageName)) {
            HCInit.initBasicData(new HCInit.BasicData()
                    .setLogLevel(HCInit.LOG_D)
                    .setTag("AutoSEffSwitch")
                    .setModulePackageName("com.hchen.autoseffswitch")
            );
            HCInit.initLoadPackageParam(lpparam);
            init(lpparam.packageName, lpparam);
        }
    }

    public void init(String pkg, XC_LoadPackage.LoadPackageParam loadPackageParam) {
        switch (pkg) {
            case "android" -> {
                new CmdHelper().onLoadPackage();
            }
            case "com.miui.misound" -> {
                String hostDir = loadPackageParam.appInfo.sourceDir;
                System.loadLibrary("dexkit");
                DexKitBridge dexKitBridge = DexKitBridge.create(hostDir);
                new AutoSEffSwitch(dexKitBridge).onLoadPackage();
                dexKitBridge.close();
            }
        }
    }
}
