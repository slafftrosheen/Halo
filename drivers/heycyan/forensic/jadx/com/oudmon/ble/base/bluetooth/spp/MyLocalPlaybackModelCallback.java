/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.os.Bundle
 */
package com.oudmon.ble.base.bluetooth.spp;

import android.os.Bundle;

public interface MyLocalPlaybackModelCallback {
    public void onGetFileHeaderReport(int var1, long var2);

    public void onGetFileContentReport(int var1, byte[] var2);

    public void onGetFileFooterReport(int var1, byte[] var2);

    public void onAddOrDeleteSongToPlaylistReport(int var1);

    public void onDeleteSingleSongReport(int var1);

    public void onDeleteAllSongReport(int var1);

    public void onGetDeviceInfoReport(Bundle var1);

    public void onEnterSongTransferModeReport(boolean var1);

    public void onExitSongTransferModeReport(boolean var1);

    public void onCancelTransferReport(boolean var1);

    public void onWriteSuccessReport(int var1);

    public void onWriteFailedReport();

    public void onTransferWasValidReport(int var1);
}

