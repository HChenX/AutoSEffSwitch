package com.hchen.autoseffswitch;

import com.hchen.autoseffswitch.misound.AutoSEffSwitch;
import com.hchen.hooktool.HCHook;
import com.hchen.hooktool.HookInit;

import org.luckypray.dexkit.DexKitBridge;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedInit implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("com.miui.misound".equals(lpparam.packageName)) {
            HookInit.setTAG("AutoSEffSwitch");
            HookInit.initLoadPackageParam(lpparam);
            String hostDir=lpparam.appInfo.sourceDir;
            System.loadLibrary("dexkit");
            DexKitBridge dexKitBridge = DexKitBridge.create(hostDir);
            new AutoSEffSwitch().init(new HCHook(),dexKitBridge);
            dexKitBridge.close();
        }
    }
}
