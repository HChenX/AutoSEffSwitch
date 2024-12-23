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
package com.hchen.autoseffswitch.hook.misound.backups;

import android.content.Context;

import com.hchen.hooktool.tool.PrefsTool;
import com.hchen.hooktool.tool.itool.IPrefsApply;

import java.util.Map;

/**
 * 基本的状态储存类。
 *
 * @author 焕晨HChen
 * */
public class BackupsUtils {
    private IPrefsApply iPrefsApply;

    public BackupsUtils(Context context) {
        try {
            iPrefsApply = PrefsTool.prefs(context, "effect_state_backups");
            iPrefsApply.editor().putBoolean("support_backups", true).apply();
        } catch (Throwable e) {
            iPrefsApply = null;
        }
    }

    public void saveDolbyState(boolean enable) {
        iPrefsApply.editor().putBoolean("dolby_state", enable).apply();
    }

    public void saveMiSoundState(boolean enable) {
        iPrefsApply.editor().putBoolean("misound_state", enable).apply();
    }

    public void saveSpatializerState(boolean enable) {
        iPrefsApply.editor().putBoolean("spatializer_state", enable).apply();
    }

    public void save3dSurroundState(boolean enable) {
        iPrefsApply.editor().putBoolean("3dsurround_state", enable).apply();
    }

    public void saveAnyState(String s, boolean enable) {
        iPrefsApply.editor().putBoolean(s, enable).apply();
    }

    public boolean getDolbyState() {
        return iPrefsApply.getBoolean("dolby_state", false);
    }

    public boolean getMiSoundState() {
        return iPrefsApply.getBoolean("misound_state", false);
    }

    public boolean getSpatializerState() {
        return iPrefsApply.getBoolean("spatializer_state", false);
    }

    public boolean get3dSurroundState() {
        return iPrefsApply.getBoolean("3dsurround_state", false);
    }

    public Map<String, ?> getAllState() {
        return iPrefsApply.getAll();
    }

    public void clearAll() {
        iPrefsApply.editor().clear().apply();
    }

    public boolean supportBackups() {
        return iPrefsApply != null && iPrefsApply.getBoolean("support_backups", false);
    }
}
