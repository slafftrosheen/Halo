/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 *  android.util.SparseArray
 */
package com.oudmon.ble.base.bluetooth;

import android.util.Log;
import android.util.SparseArray;
import com.oudmon.ble.base.bluetooth.BeanFactory;
import com.oudmon.ble.base.bluetooth.BleOperateManager;
import com.oudmon.ble.base.communication.Constants;
import com.oudmon.ble.base.communication.ICommandResponse;
import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.ble.base.request.LocalWriteRequest;
import com.oudmon.qc_utils.bytes.DataTransferUtils;
import java.util.Arrays;

public class QCDataParser {
    public static final String TAG = "GLASSES_LOG";
    private static SparseArray<BaseRspCmd> tempRspDataSparseArray = new SparseArray();

    public static boolean checkCrc(byte[] data) {
        int crc = 0;
        for (int i = 0; i < data.length - 1; ++i) {
            crc += data[i];
        }
        return data[data.length - 1] == (byte)(crc & 0xFF);
    }

    public static boolean parserAndDispatchReqData(byte[] data) {
        ICommandResponse<BaseRspCmd> iOpResponse;
        int notifyKey = data[0] & ~Constants.FLAG_MASK_ERROR;
        int status = data[0] & Constants.FLAG_MASK_ERROR;
        LocalWriteRequest localWriteRequest = BleOperateManager.getInstance().getLocalWriteRequestConcurrentHashMap().get(notifyKey);
        if (localWriteRequest != null && (iOpResponse = localWriteRequest.getiOpResponse()) != null) {
            BaseRspCmd tempBaseRspCmd = (BaseRspCmd)tempRspDataSparseArray.get(notifyKey);
            if (tempBaseRspCmd == null) {
                try {
                    tempBaseRspCmd = BeanFactory.createBean(notifyKey, localWriteRequest.getType());
                }
                catch (Exception e) {
                    e.printStackTrace();
                    BleOperateManager.getInstance().getLocalWriteRequestConcurrentHashMap().clear();
                }
            }
            if (tempBaseRspCmd != null) {
                tempBaseRspCmd.setCmdType(notifyKey);
                boolean needNext = tempBaseRspCmd.acceptData(Arrays.copyOfRange(data, 1, data.length - 1));
                if (needNext) {
                    tempRspDataSparseArray.put(notifyKey, (Object)tempBaseRspCmd);
                    return true;
                }
                iOpResponse.onDataResponse(tempBaseRspCmd);
                tempRspDataSparseArray.delete(notifyKey);
                return true;
            }
        }
        return false;
    }

    public static boolean parserAndDispatchNotifyData(SparseArray<ICommandResponse> sparseArray, byte[] data) {
        int notifyKey = data[0] & ~Constants.FLAG_MASK_ERROR;
        int status = data[0] & Constants.FLAG_MASK_ERROR;
        ICommandResponse iOpResponse = (ICommandResponse)sparseArray.get(notifyKey);
        Log.i((String)TAG, (String)("notifyKey: " + DataTransferUtils.getHexString(DataTransferUtils.intToBytes(notifyKey))));
        if (iOpResponse != null) {
            BaseRspCmd tempBaseRspCmd = (BaseRspCmd)tempRspDataSparseArray.get(notifyKey);
            if (tempBaseRspCmd == null) {
                tempBaseRspCmd = BeanFactory.createBean(notifyKey, 0);
            }
            if (tempBaseRspCmd != null) {
                boolean needNext = tempBaseRspCmd.acceptData(Arrays.copyOfRange(data, 1, data.length - 1));
                if (needNext) {
                    tempRspDataSparseArray.put(notifyKey, (Object)tempBaseRspCmd);
                    return true;
                }
                iOpResponse.onDataResponse(tempBaseRspCmd);
                tempRspDataSparseArray.delete(notifyKey);
                return true;
            }
        }
        return false;
    }
}

