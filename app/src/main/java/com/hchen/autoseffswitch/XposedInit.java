package com.hchen.autoseffswitch;

import com.hchen.hooktool.HookInit;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedInit implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("com.miui.misound".equals(lpparam.packageName)) {
            HookInit.setTAG("AutoSEffSwitch");
            HookInit.initLoadPackageParam(lpparam);
        }
    }
}
