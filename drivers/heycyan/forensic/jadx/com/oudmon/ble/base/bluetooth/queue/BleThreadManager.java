/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.bluetooth.queue;

import com.oudmon.ble.base.bluetooth.BleOperateManager;
import com.oudmon.ble.base.bluetooth.queue.BleConsumer;
import com.oudmon.ble.base.bluetooth.queue.BleDataBean;
import java.util.Random;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

public class BleThreadManager {
    private static BleThreadManager instance;
    private BlockingDeque<BleDataBean> queue = new LinkedBlockingDeque<BleDataBean>(20);
    private BleConsumer bleConsumer;

    private BleThreadManager() {
        if (this.bleConsumer == null) {
            this.bleConsumer = new BleConsumer("bleConsumer-" + new Random().nextInt(), this.queue);
            this.bleConsumer.start();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static BleThreadManager getInstance() {
        if (instance == null) {
            Class<BleThreadManager> clazz = BleThreadManager.class;
            synchronized (BleThreadManager.class) {
                if (instance == null) {
                    instance = new BleThreadManager();
                }
                // ** MonitorExit[var0] (shouldn't be in output)
                return instance;
            }
        }
        return instance;
    }

    public void addData(BleDataBean bean) {
        try {
            if (!BleOperateManager.getInstance().isConnected()) {
                return;
            }
            this.queue.putLast(bean);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void clean() {
        this.queue.clear();
    }
}

