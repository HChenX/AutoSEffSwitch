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
package com.hchen.autoseffswitch.hook.system;

import static com.hchen.hooktool.log.XposedLog.logE;

import android.content.Context;
import android.provider.Settings;

import com.hchen.hooktool.BaseHC;
import com.hchen.hooktool.hook.IHook;

import java.io.PrintWriter;

/**
 * 注入命令。
 *
 * @author 焕晨HChen
 * @deprecated
 */
@Deprecated
public final class CmdHelper extends BaseHC {
    private Context mContext;

    @Override
    public void init() {
        hookMethod("com.android.server.pm.PackageManagerShellCommand",
                "onCommand", String.class,
                new IHook() {
                    @Override
                    public void before() {
                        String cmd = (String) getArgs(0);
                        mContext = (Context) getThisField("mContext");
                        if (mContext == null) {
                            logE(TAG, "onCommand context is null!!");
                            return;
                        }
                        if (cmd == null) return;
                        if ("aseff".equals(cmd)) {
                            PrintWriter getOutPrintWriter = (PrintWriter) callThisMethod("getOutPrintWriter");
                            String getNextOption = (String) callThisMethod("getNextOption");
                            if (getOutPrintWriter == null) {
                                logE(TAG, "onCommand getOutPrintWriter is null!!");
                                setResult(-1);
                                return;
                            }
                            if (getNextOption == null) {
                                getOutPrintWriter.println("[pinning] must be followed by an option! For details, please refer to -h");
                                setResult(-1);
                                return;
                            }
                            switch (getNextOption) {
                                case "-h", "--help" -> {
                                    help(getOutPrintWriter);
                                    setResult(0);
                                }
                                case "-s" -> {
                                    String next = (String) callThisMethod("getNextArgRequired");
                                    if (next == null) {
                                        getOutPrintWriter.println("-s must be followed by a numerical parameter! For details, please refer to - h.");
                                        setResult(-1);
                                        return;
                                    }
                                    setUUID(this, getOutPrintWriter, next);
                                }
                            }
                        }
                    }
                }
        );
    }

    private void help(PrintWriter printWriter) {
        printWriter.println("AutoSEffSwitch Helper: ");
        printWriter.println("    [-s | set <UUID>]: ");
        printWriter.println("    Set the UUID identifier for the sound effect.");
        printWriter.println("    sample:[pm aseff -s \"<UUID>\"]");
        printWriter.println("    设置音效效果UUID标识符。");
        printWriter.println("    举例:[pm aseff -s \"<UUID>\"]");
        printWriter.println("-------------------------------------");
        printWriter.println("From AutoSEffSwitch, Version v.1.6, Author: HChenX");
    }

    private void setUUID(IHook iHook, PrintWriter printWriter, String UUID) {
        if (mContext != null) {
            try {
                Settings.Global.putString(mContext.getContentResolver(), "aseff_uuid", UUID);
                printWriter.println("Set UUID successfully: " + UUID);
                iHook.setResult(0);
            } catch (Throwable e) {
                printWriter.println("Something went wrong: " + e);
                iHook.setResult(-1);
            }
        } else {
            printWriter.println("The context is null!");
            iHook.setResult(-1);
        }
    }
}
