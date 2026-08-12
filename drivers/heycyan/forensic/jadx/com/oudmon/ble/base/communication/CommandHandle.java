/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication;

import android.util.Log;
import com.oudmon.ble.base.bluetooth.BleOperateManager;
import com.oudmon.ble.base.communication.Constants;
import com.oudmon.ble.base.communication.ICommandResponse;
import com.oudmon.ble.base.communication.req.BaseReqCmd;
import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import com.oudmon.ble.base.request.LocalWriteRequest;
import com.oudmon.ble.base.request.ReadRequest;

public class CommandHandle {
    private static final String TAG = "CommandHandle";
    private static CommandHandle odmHandle;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static CommandHandle getInstance() {
        if (odmHandle != null) return odmHandle;
        Class<CommandHandle> clazz = CommandHandle.class;
        synchronized (CommandHandle.class) {
            if (odmHandle != null) return odmHandle;
            odmHandle = new CommandHandle();
            // ** MonitorExit[var0] (shouldn't be in output)
            return odmHandle;
        }
    }

    private CommandHandle() {
    }

    private <T extends BaseRspCmd> LocalWriteRequest<T> getWriteRequest(byte[] data) {
        LocalWriteRequest writeRequest = new LocalWriteRequest(Constants.UUID_SERVICE, Constants.UUID_WRITE);
        writeRequest.setValue(data);
        return writeRequest;
    }

    public ReadRequest getReadHwRequest() {
        return new ReadRequest(Constants.SERVICE_DEVICE_INFO, Constants.CHAR_HW_REVISION);
    }

    public ReadRequest getReadFmRequest() {
        return new ReadRequest(Constants.SERVICE_DEVICE_INFO, Constants.CHAR_FIRMWARE_REVISION);
    }

    public void executeReqCmd(BaseReqCmd reqCmd, ICommandResponse iOpResponse) {
        if (!BleOperateManager.getInstance().isConnected()) {
            Log.i((String)TAG, (String)("\u8bbe\u5907\u5df2\u7ecf\u65ad\u5f00\uff1a" + ByteUtil.byteArrayToString(reqCmd.getData())));
            return;
        }
        LocalWriteRequest localWriteRequest = this.getWriteRequest(reqCmd.getData());
        int notifyKey = localWriteRequest.getValue()[0] & ~Constants.FLAG_MASK_ERROR;
        localWriteRequest.setiOpResponse(iOpResponse);
        if (iOpResponse != null) {
            BleOperateManager.getInstance().getLocalWriteRequestConcurrentHashMap().put(notifyKey, localWriteRequest);
        }
        BleOperateManager.getInstance().execute(localWriteRequest);
    }

    public void executeReqCmdNoCallback(BaseReqCmd reqCmd) {
        if (!BleOperateManager.getInstance().isConnected()) {
            Log.i((String)TAG, (String)"\u8bbe\u5907\u5df2\u7ecf\u65ad\u5f00");
            return;
        }
        LocalWriteRequest localWriteRequest = this.getWriteRequest(reqCmd.getData());
        BleOperateManager.getInstance().execute(localWriteRequest);
    }

    public void execReadCmd(ReadRequest reqCmd) {
        BleOperateManager.getInstance().execute(reqCmd);
    }
}

