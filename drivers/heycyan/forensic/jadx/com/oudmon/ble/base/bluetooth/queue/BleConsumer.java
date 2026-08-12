/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.bluetooth.queue;

import android.util.Log;
import com.oudmon.ble.base.bluetooth.BleOperateManager;
import com.oudmon.ble.base.bluetooth.queue.BleDataBean;
import com.oudmon.ble.base.communication.utils.CRC16;
import com.oudmon.ble.base.request.WriteRequest;
import com.oudmon.qc_utils.bytes.DataTransferUtils;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

public class BleConsumer
extends Thread {
    private static final UUID SERIAL_PORT_SERVICE = UUID.fromString("de5bf728-d711-4e47-af26-65e3012a5dc7");
    private static final UUID SERIAL_PORT_CHARACTER_NOTIFY = UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7");
    private static final UUID SERIAL_PORT_CHARACTER_WRITE = UUID.fromString("de5bf72a-d711-4e47-af26-65e3012a5dc7");
    BlockingQueue<BleDataBean> blockingQueue;

    public BleConsumer(String name, BlockingQueue<BleDataBean> blockingQueue) {
        this.blockingQueue = blockingQueue;
    }

    @Override
    public void run() {
        try {
            block2: while (true) {
                BleDataBean bean = this.blockingQueue.take();
                int index = 0;
                if (bean.getSleepTime() > 0) {
                    Thread.sleep(bean.getSleepTime());
                }
                int mPackageLength = bean.getSubLength();
                while (true) {
                    if (index * mPackageLength >= bean.getData().length) continue block2;
                    BleOperateManager.getInstance().execute(this.getWriteRequest(Arrays.copyOfRange(bean.getData(), index * mPackageLength, index * mPackageLength + Math.min(mPackageLength, bean.getData().length - index * mPackageLength))));
                    ++index;
                }
                break;
            }
        }
        catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }
    }

    private byte[] addHeader(int cmdId, byte[] data) {
        byte[] pocket = new byte[(data == null ? 0 : data.length) + 6];
        pocket[0] = -68;
        pocket[1] = (byte)cmdId;
        if (data != null && data.length > 0) {
            System.arraycopy(DataTransferUtils.shortToBytes((short)data.length), 0, pocket, 2, 2);
            System.arraycopy(DataTransferUtils.shortToBytes((short)CRC16.calcCrc16(data)), 0, pocket, 4, 2);
            System.arraycopy(data, 0, pocket, 6, data.length);
        } else {
            pocket[4] = -1;
            pocket[5] = -1;
        }
        return pocket;
    }

    public WriteRequest getWriteRequest(byte[] data) {
        Log.i((String)"GLASSES_LOG", (String)("getWriteRequest: data=" + DataTransferUtils.getHexString(data)));
        WriteRequest noRspInstance = WriteRequest.getNoRspInstance(SERIAL_PORT_SERVICE, SERIAL_PORT_CHARACTER_WRITE);
        noRspInstance.setValue(data);
        return noRspInstance;
    }
}

