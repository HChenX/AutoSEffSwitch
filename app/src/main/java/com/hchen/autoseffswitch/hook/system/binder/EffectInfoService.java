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
package com.hchen.autoseffswitch.hook.system.binder;

import android.os.RemoteException;

import com.hchen.autoseffswitch.IEffectInfo;
import com.hchen.autoseffswitch.hook.system.AutoEffectSwitchForSystem;
import com.hchen.autoseffswitch.hook.system.control.BaseEffectControl;

import java.util.Collections;
import java.util.Map;

/**
 * Binder 本地实现
 *
 * @author 焕晨HChen
 */
public class EffectInfoService extends IEffectInfo.Stub {
    private final BaseEffectControl mBaseEffectControl;

    public EffectInfoService(BaseEffectControl baseEffectControl) {
        mBaseEffectControl = baseEffectControl;
    }

    @Override
    public boolean isEarphoneConnection() throws RemoteException {
        return AutoEffectSwitchForSystem.isEarphoneConnection;
    }

    @Override
    public Map<String, String> getEffectSupportMap() throws RemoteException {
        if (mBaseEffectControl == null)
            return Collections.emptyMap();
        return mBaseEffectControl.getEffectSupportMap();
    }

    @Override
    public Map<String, String> getEffectAvailableMap() throws RemoteException {
        if (mBaseEffectControl == null)
            return Collections.emptyMap();
        return mBaseEffectControl.getEffectAvailableMap();
    }

    @Override
    public Map<String, String> getEffectActiveMap() throws RemoteException {
        if (mBaseEffectControl == null)
            return Collections.emptyMap();
        return mBaseEffectControl.getEffectActiveMap();
    }

    @Override
    public Map<String, String> getEffectEnabledMap() throws RemoteException {
        if (mBaseEffectControl == null)
            return Collections.emptyMap();
        return mBaseEffectControl.getEffectEnabledMap();
    }
}
