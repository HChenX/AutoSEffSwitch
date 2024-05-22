package com.hchen.autoseffswitch.system;

import static com.hchen.hooktool.log.XposedLog.logE;

import android.content.Context;
import android.provider.Settings;

import com.hchen.hooktool.BaseHook;
import com.hchen.hooktool.callback.IAction;
import com.hchen.hooktool.tool.ParamTool;
import com.hchen.hooktool.tool.StaticTool;

import java.io.PrintWriter;

public class CmdHelper extends BaseHook {
    private Context mContext;
    private static final String TAG = "CmdHelper";

    @Override
    public void init() {
        classTool.findClass("PackageManagerShellCommand",
                        "com.android.server.pm.PackageManagerShellCommand")
                .getMethod("onCommand", String.class)
                .hook(new IAction() {
                    @Override
                    public void before(ParamTool param, StaticTool staticTool) {
                        String cmd = param.first();
                        mContext = param.getField("mContext");
                        if (mContext == null) {
                            logE(TAG, "onCommand context is null!!");
                            return;
                        }
                        if (cmd == null) return;
                        if ("aseff".equals(cmd)) {
                            PrintWriter getOutPrintWriter = param.callMethod("getOutPrintWriter");
                            String getNextOption = param.callMethod("getNextOption");
                            if (getOutPrintWriter == null) {
                                logE(TAG, "onCommand getOutPrintWriter is null!!");
                                param.setResult(-1);
                                return;
                            }
                            if (getNextOption == null) {
                                getOutPrintWriter.println("[pinning] must be followed by an option! For details, please refer to -h");
                                param.setResult(-1);
                                return;
                            }
                            switch (getNextOption) {
                                case "-h", "--help" -> {
                                    help(getOutPrintWriter);
                                    param.setResult(0);
                                }
                                case "-s" -> {
                                    try {
                                        String next = param.callMethod("getNextArgRequired");
                                        setUUID(param, getOutPrintWriter, next);
                                    } catch (IllegalArgumentException e) {
                                        getOutPrintWriter.println("-s must be followed by a numerical parameter! For details, please refer to - h\n" + e);
                                        param.setResult(-1);
                                    }
                                }
                            }
                        }
                    }
                });

    }

    private void help(PrintWriter printWriter) {
        printWriter.println("AutoSEffSwitch Helper: ");
        printWriter.println("    [-s | set <UUID>]: ");
        printWriter.println("    Set the UUID identifier for the sound effect.");
        printWriter.println("    sample:[pm aseff -s \"<UUID>\"]");
        printWriter.println("    设置音效效果UUID标识符。");
        printWriter.println("    举例:[pm aseff -s \"<UUID>\"]");
        printWriter.println("-------------------------------------");
        printWriter.println("From AutoSEffSwitch, Version v.1.0, Author: HChenX");
    }

    private void setUUID(ParamTool paramTool, PrintWriter printWriter, String UUID) {
        if (mContext != null) {
            try {
                Settings.Global.putString(mContext.getContentResolver(), "aseff_uuid", UUID);
                printWriter.println("Set UUID successfully: " + UUID);
                paramTool.setResult(0);
            } catch (Throwable e) {
                printWriter.println("Something went wrong: " + e);
                paramTool.setResult(-1);
            }
        } else {
            printWriter.println("The context is null!");
            paramTool.setResult(-1);
        }
    }
}
