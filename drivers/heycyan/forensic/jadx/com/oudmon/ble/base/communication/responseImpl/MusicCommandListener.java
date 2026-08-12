/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.media.AudioManager
 *  android.view.KeyEvent
 */
package com.oudmon.ble.base.communication.responseImpl;

import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;
import com.oudmon.ble.base.communication.CommandHandle;
import com.oudmon.ble.base.communication.ICommandResponse;
import com.oudmon.ble.base.communication.req.MusicSwitchReq;
import com.oudmon.ble.base.communication.rsp.MusicCommandRsp;

public class MusicCommandListener
implements ICommandResponse<MusicCommandRsp> {
    private Context mContext;

    public MusicCommandListener(Context context) {
        this.mContext = context;
    }

    @Override
    public void onDataResponse(MusicCommandRsp resultEntity) {
        if (resultEntity.getStatus() == 0) {
            if (resultEntity.getAction() == 1) {
                KeyEvent down = new KeyEvent(0, 85);
                KeyEvent down2 = new KeyEvent(0, 79);
                KeyEvent up = new KeyEvent(1, 85);
                this.controlMusic(this.mContext, down, up, down2);
            } else if (resultEntity.getAction() == 2) {
                KeyEvent down = new KeyEvent(0, 88);
                KeyEvent up = new KeyEvent(1, 88);
                this.controlMusic(this.mContext, down, up);
            } else if (resultEntity.getAction() == 3) {
                KeyEvent down = new KeyEvent(0, 87);
                KeyEvent up = new KeyEvent(1, 87);
                this.controlMusic(this.mContext, down, up);
            } else if (resultEntity.getAction() == 4) {
                AudioManager audioManager = (AudioManager)this.mContext.getSystemService("audio");
                audioManager.adjustStreamVolume(3, 1, 1);
            } else if (resultEntity.getAction() == 5) {
                AudioManager audioManager = (AudioManager)this.mContext.getSystemService("audio");
                audioManager.adjustStreamVolume(3, -1, 1);
            }
        }
    }

    private void controlMusic(Context context, KeyEvent down, KeyEvent up) {
        AudioManager audioManager = (AudioManager)context.getSystemService("audio");
        audioManager.dispatchMediaKeyEvent(down);
        audioManager.dispatchMediaKeyEvent(up);
        boolean flag = audioManager.isMusicActive();
        if (flag) {
            CommandHandle.getInstance().executeReqCmd(MusicSwitchReq.getNewWriteInstance(false, 0, this.getSystemVolume(this.mContext), ""), null);
        }
    }

    private void controlMusic(Context context, KeyEvent down, KeyEvent up, KeyEvent down1) {
        AudioManager audioManager = (AudioManager)context.getSystemService("audio");
        audioManager.dispatchMediaKeyEvent(down);
        audioManager.dispatchMediaKeyEvent(up);
        audioManager.dispatchMediaKeyEvent(down1);
        boolean flag = audioManager.isMusicActive();
        if (flag) {
            CommandHandle.getInstance().executeReqCmd(MusicSwitchReq.getNewWriteInstance(false, 0, this.getSystemVolume(this.mContext), ""), null);
        }
    }

    private int getSystemVolume(Context context) {
        AudioManager audioManager = (AudioManager)context.getSystemService("audio");
        int max = audioManager.getStreamMaxVolume(3);
        int current = audioManager.getStreamVolume(3);
        return current * 100 / max;
    }
}

