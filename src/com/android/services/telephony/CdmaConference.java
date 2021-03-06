/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.services.telephony;

import android.content.Context;
import android.content.res.Resources;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.PhoneAccountHandle;
import android.telecom.PhoneCapabilities;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.phone.PhoneGlobals;
import com.android.phone.common.R;

import java.util.List;

/**
 * CDMA-based conference call.
 */
public class CdmaConference extends Conference {
    private int mCapabilities;

    public CdmaConference(PhoneAccountHandle phoneAccount) {
        super(phoneAccount);
        setActive();
    }

    public void updateCapabilities(int capabilities) {
        capabilities |=  PhoneCapabilities.MUTE | PhoneCapabilities.GENERIC_CONFERENCE;
        setCapabilities(capabilities);
    }

    /**
     * Invoked when the Conference and all it's {@link Connection}s should be disconnected.
     */
    @Override
    public void onDisconnect() {
        Call call = getOriginalCall();
        if (call != null) {
            Log.d(this, "Found multiparty call to hangup for conference.");
            try {
                call.hangup();
            } catch (CallStateException e) {
                Log.e(this, e, "Exception thrown trying to hangup conference");
            }
        }
    }

    @Override
    public void onSeparate(Connection connection) {
        Log.e(this, new Exception(), "Separate not supported for CDMA conference call.");
    }

    @Override
    public void onHold() {
        Log.e(this, new Exception(), "Hold not supported for CDMA conference call.");
    }

    /**
     * Invoked when the conference should be moved from hold to active.
     */
    @Override
    public void onUnhold() {
        Log.e(this, new Exception(), "Unhold not supported for CDMA conference call.");
    }

    @Override
    public void onMerge() {
        Log.i(this, "Merging CDMA conference call.");
        // Can only merge once
        mCapabilities &= ~PhoneCapabilities.MERGE_CONFERENCE;
        // Once merged, swap is enabled.
        if (isSwapSupportedAfterMerge()){
            mCapabilities |= PhoneCapabilities.SWAP_CONFERENCE;
        }
        updateCapabilities(mCapabilities);
        sendFlash();
    }

    @Override
    public void onPlayDtmfTone(char c) {
        final CdmaConnection connection = getFirstConnection();
        if (connection != null) {
            connection.onPlayDtmfTone(c);
        } else {
            Log.w(this, "No CDMA connection found while trying to play dtmf tone.");
        }
    }

    @Override
    public void onStopDtmfTone() {
        final CdmaConnection connection = getFirstConnection();
        if (connection != null) {
            connection.onStopDtmfTone();
        } else {
            Log.w(this, "No CDMA connection found while trying to stop dtmf tone.");
        }
    }

    @Override
    public void onSwap() {
        Log.i(this, "Swapping CDMA conference call.");
        sendFlash();
    }

    private void sendFlash() {
        Call call = getOriginalCall();
        if (call != null) {
            try {
                // For CDMA calls, this just sends a flash command.
                call.getPhone().switchHoldingAndActive();
            } catch (CallStateException e) {
                Log.e(this, e, "Error while trying to send flash command.");
            }
        }
    }

    private Call getMultipartyCallForConnection(Connection connection) {
        com.android.internal.telephony.Connection radioConnection =
                getOriginalConnection(connection);
        if (radioConnection != null) {
            Call call = radioConnection.getCall();
            if (call != null && call.isMultiparty()) {
                return call;
            }
        }
        return null;
    }

    private Call getOriginalCall() {
        List<Connection> connections = getConnections();
        if (!connections.isEmpty()) {
            com.android.internal.telephony.Connection originalConnection =
                    getOriginalConnection(connections.get(0));
            if (originalConnection != null) {
                return originalConnection.getCall();
            }
        }
        return null;
    }

    /**
     * Return whether network support swap after merge conference call.
     *
     * @return true to support, false not support.
     */
    private final boolean isSwapSupportedAfterMerge()
    {
        boolean supportSwapAfterMerge = true;
        Context context = PhoneGlobals.getInstance();

        if (context != null) {
            Resources r = context.getResources();
            if (r != null) {
                supportSwapAfterMerge = r.getBoolean(R.bool.support_swap_after_merge);
                Log.d(this, "Current network support swap after call merged capability is "
                        + supportSwapAfterMerge);
            }
        }
        return supportSwapAfterMerge;
    }

    private com.android.internal.telephony.Connection getOriginalConnection(Connection connection) {
        if (connection instanceof CdmaConnection) {
            return ((CdmaConnection) connection).getOriginalConnection();
        } else {
            Log.e(this, null, "Non CDMA connection found in a CDMA conference");
            return null;
        }
    }

    private CdmaConnection getFirstConnection() {
        final List<Connection> connections = getConnections();
        if (connections.isEmpty()) {
            return null;
        }
        return (CdmaConnection) connections.get(0);
    }
}
