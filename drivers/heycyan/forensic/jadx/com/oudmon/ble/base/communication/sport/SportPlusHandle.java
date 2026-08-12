/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 *  android.util.SparseIntArray
 */
package com.oudmon.ble.base.communication.sport;

import android.util.Log;
import android.util.SparseIntArray;
import com.oudmon.ble.base.bluetooth.BleOperateManager;
import com.oudmon.ble.base.bluetooth.OnGattEventCallback;
import com.oudmon.ble.base.communication.Constants;
import com.oudmon.ble.base.communication.sport.SportLocation;
import com.oudmon.ble.base.communication.sport.SportPlusEntity;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import com.oudmon.ble.base.communication.utils.CRC16;
import com.oudmon.ble.base.request.WriteRequest;
import com.oudmon.qc_utils.bytes.DataTransferUtils;
import com.oudmon.qc_utils.date.DateUtil;
import java.util.ArrayList;
import java.util.List;

public class SportPlusHandle {
    private static final String TAG = "Jxr35";
    private IOpResult iOpResult;
    private int mSportIndex = 0;
    private List<SportPlusEntity> mSportEntities = new ArrayList<SportPlusEntity>();
    private List<SportLocation> mLocations = new ArrayList<SportLocation>();
    private int mTotalCount = 0;
    private int mReceivedCount = 0;
    private boolean mSummaryReceiving = false;
    private boolean mDetailsReceiving = false;
    private byte[] mSummary;
    private byte[] mDetails;
    private int mPackageCount = 0;
    private int mPackageIndex = 0;
    private int mSampleSecond = 0;
    private int mDataLength = 0;
    private SparseIntArray mDataTypeArray = new SparseIntArray();
    public OnGattEventCallback callback = new OnGattEventCallback(){

        @Override
        public void onReceivedData(String uuid, byte[] data) {
            if (data != null) {
                try {
                    if ((data[0] & 0xFF) == 188 && data[1] == 66) {
                        SportPlusHandle.this.mTotalCount = DataTransferUtils.bytesToShort(data, 2);
                        SportPlusHandle.this.mReceivedCount = data.length - 6;
                        SportPlusHandle.access$202(SportPlusHandle.this, new byte[SportPlusHandle.this.mTotalCount]);
                        System.arraycopy(data, 6, SportPlusHandle.this.mSummary, 0, SportPlusHandle.this.mReceivedCount);
                        SportPlusHandle.this.mSummaryReceiving = SportPlusHandle.this.mReceivedCount < SportPlusHandle.this.mTotalCount;
                        if (!SportPlusHandle.this.mSummaryReceiving) {
                            Log.i((String)SportPlusHandle.TAG, (String)("onReceiver All Summary data: " + DataTransferUtils.getHexString(SportPlusHandle.this.mSummary)));
                            SportPlusHandle.this.parseSummary(SportPlusHandle.this.mSummary);
                            SportPlusHandle.this.iOpResult.onSummary(1, SportPlusHandle.this.mSportEntities);
                            SportPlusHandle.this.executeRequest();
                        }
                    } else if (SportPlusHandle.this.mSummaryReceiving) {
                        System.arraycopy(data, 0, SportPlusHandle.this.mSummary, SportPlusHandle.this.mReceivedCount, data.length);
                        SportPlusHandle.this.mReceivedCount += data.length;
                        SportPlusHandle.this.mSummaryReceiving = SportPlusHandle.this.mReceivedCount < SportPlusHandle.this.mTotalCount;
                        Log.i((String)SportPlusHandle.TAG, (String)("onReceivedData.. mTotalCount: " + SportPlusHandle.this.mTotalCount + ", mReceivedCount: " + SportPlusHandle.this.mReceivedCount + ", mSummaryReceiving: " + SportPlusHandle.this.mSummaryReceiving));
                        if (!SportPlusHandle.this.mSummaryReceiving) {
                            Log.i((String)SportPlusHandle.TAG, (String)("onReceiver All Summary data: " + DataTransferUtils.getHexString(SportPlusHandle.this.mSummary)));
                            SportPlusHandle.this.parseSummary(SportPlusHandle.this.mSummary);
                            SportPlusHandle.this.iOpResult.onSummary(1, SportPlusHandle.this.mSportEntities);
                            SportPlusHandle.this.executeRequest();
                        }
                    } else if ((data[0] & 0xFF) == 188 && data[1] == 68) {
                        short mTotalCount = DataTransferUtils.bytesToShort(data, 2);
                        int mReceivedCount = data.length - 6;
                        byte[] request = new byte[mTotalCount];
                        System.arraycopy(data, 6, request, 0, mReceivedCount);
                        SportPlusHandle.this.parseRequest(request);
                    } else if ((data[0] & 0xFF) == 188 && data[1] == 69) {
                        SportPlusHandle.this.mTotalCount = DataTransferUtils.bytesToShort(data, 2);
                        SportPlusHandle.this.mReceivedCount = data.length - 6;
                        SportPlusHandle.access$902(SportPlusHandle.this, new byte[SportPlusHandle.this.mTotalCount]);
                        System.arraycopy(data, 6, SportPlusHandle.this.mDetails, 0, SportPlusHandle.this.mReceivedCount);
                        SportPlusHandle.this.mDetailsReceiving = SportPlusHandle.this.mReceivedCount < SportPlusHandle.this.mTotalCount;
                        Log.i((String)SportPlusHandle.TAG, (String)("onReceivedData.. mTotalCount: " + SportPlusHandle.this.mTotalCount + ", mReceivedCount: " + SportPlusHandle.this.mReceivedCount + ", mDetailsReceiving: " + SportPlusHandle.this.mDetailsReceiving));
                        if (!SportPlusHandle.this.mDetailsReceiving) {
                            SportPlusHandle.this.parseDetails(SportPlusHandle.this.mDetails);
                        }
                    } else if (SportPlusHandle.this.mDetailsReceiving) {
                        System.arraycopy(data, 0, SportPlusHandle.this.mDetails, SportPlusHandle.this.mReceivedCount, data.length);
                        SportPlusHandle.this.mReceivedCount += data.length;
                        SportPlusHandle.this.mDetailsReceiving = SportPlusHandle.this.mReceivedCount < SportPlusHandle.this.mTotalCount;
                        Log.i((String)SportPlusHandle.TAG, (String)("onReceivedData.. mTotalCount: " + SportPlusHandle.this.mTotalCount + ", mReceivedCount: " + SportPlusHandle.this.mReceivedCount + ", mDetailsReceiving: " + SportPlusHandle.this.mDetailsReceiving));
                        if (!SportPlusHandle.this.mDetailsReceiving) {
                            SportPlusHandle.this.parseDetails(SportPlusHandle.this.mDetails);
                        }
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };
    private long mTime = 0L;

    private void parseSummary(byte[] data) {
        Log.i((String)TAG, (String)"===========================\u89e3\u6790Summary\u5f00\u59cb============================");
        this.mSportIndex = 0;
        this.mSportEntities.clear();
        try {
            int start = 1;
            for (int count = data[0]; count > 0; --count) {
                byte dataLength;
                int length = data[start];
                byte[] tempArray = new byte[length];
                System.arraycopy(data, start, tempArray, 0, length);
                SportPlusEntity entity = new SportPlusEntity();
                entity.mSportType = ByteUtil.byteToInt(tempArray[1]);
                Log.i((String)TAG, (String)ByteUtil.byteArrayToString(tempArray));
                Log.i((String)TAG, (String)("tempArray: " + DataTransferUtils.getHexString(tempArray) + ", sportType: " + entity.mSportType));
                int flag = 0;
                for (int sumLength = 2; sumLength < length; sumLength += dataLength) {
                    dataLength = tempArray[flag + 2];
                    byte dataType = tempArray[flag + 3];
                    byte[] dataValue = new byte[dataLength - 2];
                    System.arraycopy(tempArray, flag + 4, dataValue, 0, dataLength - 2);
                    this.setKeyValues(entity, dataType, dataValue);
                    flag += dataLength;
                }
                this.mSportEntities.add(entity);
                start += length;
            }
            Log.i((String)TAG, (String)"===========================\u89e3\u6790Summary\u7ed3\u675f============================");
        }
        catch (Exception e) {
            Log.i((String)TAG, (String)"===========================\u89e3\u6790Summary\u5f02\u5e38============================");
            e.printStackTrace();
        }
    }

    private void setKeyValues(SportPlusEntity entity, int key, byte[] values) {
        if (key == 1) {
            entity.mStartTime = DataTransferUtils.arrays2Int(values);
        } else if (key == 2) {
            entity.mDuration = DataTransferUtils.arrays2Int(values);
        } else if (key == 3) {
            entity.mDistance = DataTransferUtils.arrays2Int(values);
        } else if (key == 4) {
            entity.mCalories = DataTransferUtils.arrays2Int(values);
        } else if (key == 5) {
            entity.mSpeedAvg = DataTransferUtils.arrays2Int(values);
        } else if (key == 6) {
            entity.mSpeedMax = DataTransferUtils.arrays2Int(values);
        } else if (key == 7) {
            entity.mRateAvg = DataTransferUtils.arrays2Int(values);
        } else if (key == 8) {
            entity.mRateMin = DataTransferUtils.arrays2Int(values);
        } else if (key == 9) {
            entity.mRateMax = DataTransferUtils.arrays2Int(values);
        } else if (key == 10) {
            entity.mElevation = DataTransferUtils.arrays2Int(values);
        } else if (key == 11) {
            entity.mUphill = DataTransferUtils.arrays2Int(values);
        } else if (key == 12) {
            entity.mDownhill = DataTransferUtils.arrays2Int(values);
        } else if (key == 13) {
            entity.mStepRate = DataTransferUtils.arrays2Int(values);
        } else if (key == 14) {
            entity.mSportCount = DataTransferUtils.arrays2Int(values);
        } else if (key == 19) {
            entity.steps = DataTransferUtils.arrays2Int(values);
        }
    }

    private void executeRequest() {
        Log.i((String)TAG, (String)("executeRequest.. mSportIndex: " + this.mSportIndex + ", totalSize: " + this.mSportEntities.size()));
        if (this.mSportIndex < this.mSportEntities.size()) {
            SportPlusEntity entity = this.mSportEntities.get(this.mSportIndex);
            Log.i((String)TAG, (String)(new DateUtil((long)entity.mStartTime, true).getY_M_D_H_M_S() + "----" + DataTransferUtils.getHexString(DataTransferUtils.intToBytes(entity.mStartTime))));
            this.cmdRequest(entity.mSportType, entity.mStartTime);
        } else {
            Log.i((String)TAG, (String)("\u83b7\u53d6\u6240\u6709\u8be6\u7ec6\u6570\u636e\u7ed3\u675f mSportEntities: " + this.mSportEntities));
            this.iOpResult.onSummary(2, this.mSportEntities);
            Log.i((String)TAG, (String)("==================================onDetails cost time: " + (System.currentTimeMillis() - this.mTime)));
        }
    }

    private void parseRequest(byte[] data) {
        this.mDataLength = 0;
        this.mLocations.clear();
        if (data[0] == 0) {
            this.mPackageCount = DataTransferUtils.byte2Int(data, 1);
            this.mSampleSecond = data[3];
            Log.i((String)TAG, (String)("parseRequest.. mPackageCount: " + this.mPackageCount + ", mSampleSecond: " + this.mSampleSecond));
            if (data.length == 4 || this.mPackageCount == 0) {
                this.mDataTypeArray.clear();
                this.mSportEntities.get((int)this.mSportIndex).mLocations.clear();
                ++this.mSportIndex;
                this.executeRequest();
            } else {
                for (int index = 4; index < data.length; index += 2) {
                    this.mDataTypeArray.put((int)data[index + 1], (int)data[index]);
                    this.mDataLength += data[index];
                }
                for (int i = 0; i < this.mDataTypeArray.size(); ++i) {
                    int key = this.mDataTypeArray.keyAt(i);
                    int length = this.mDataTypeArray.get(key);
                    Log.i((String)TAG, (String)("parseRequest.. key: " + key + ", value: " + length));
                }
            }
        } else {
            ++this.mSportIndex;
            this.executeRequest();
        }
    }

    private void parseDetails(byte[] data) {
        try {
            int packageId = DataTransferUtils.byte2Int(data, 0);
            Log.i((String)TAG, (String)("parseDetails.. packageId: " + packageId + ", mPackageCount: " + this.mPackageCount));
            for (int index = 2; index < data.length; index += this.mDataLength) {
                byte[] temp = new byte[this.mDataLength];
                System.arraycopy(data, index, temp, 0, this.mDataLength);
                int flag = 0;
                SportLocation location = new SportLocation();
                for (int i = 0; i < this.mDataTypeArray.size(); ++i) {
                    int key = this.mDataTypeArray.keyAt(i);
                    int length = this.mDataTypeArray.get(key);
                    if (key != 15 && key != 16) {
                        if (key == 17) {
                            location.mRateReal = temp[flag] & 0xFF;
                        } else if (key == 18) {
                            // empty if block
                        }
                    }
                    flag += length;
                }
                this.mLocations.add(location);
            }
            if (packageId >= this.mPackageCount) {
                this.mDataTypeArray.clear();
                this.mSportEntities.get((int)this.mSportIndex).mLocations.addAll(this.mLocations);
                ++this.mSportIndex;
                this.executeRequest();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void init(IOpResult iOpResult) {
        Log.i((String)TAG, (String)"init... ");
        this.mTime = System.currentTimeMillis();
        this.iOpResult = iOpResult;
        this.mLocations.clear();
        BleOperateManager.getInstance().setCallback(this.callback);
    }

    public void cmdSummary(int time) {
        byte[] data = new byte[4];
        System.arraycopy(DataTransferUtils.intToBytes(time), 0, data, 0, 4);
        BleOperateManager.getInstance().execute(this.getWriteRequest(this.addHeader(65, data)));
    }

    public void cmdRequest(int sportType, int time) {
        byte[] data = new byte[5];
        data[0] = (byte)sportType;
        System.arraycopy(DataTransferUtils.intToBytes(time), 0, data, 1, 4);
        BleOperateManager.getInstance().execute(this.getWriteRequest(this.addHeader(67, data)));
    }

    private byte[] addHeader(int cmdid, byte[] data) {
        byte[] pocket = new byte[(data == null ? 0 : data.length) + 6];
        pocket[0] = -68;
        pocket[1] = (byte)cmdid;
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

    private WriteRequest getWriteRequest(byte[] data) {
        WriteRequest noRspInstance = WriteRequest.getNoRspInstance(Constants.SERIAL_PORT_SERVICE, Constants.SERIAL_PORT_CHARACTER_WRITE);
        noRspInstance.setValue(data);
        return noRspInstance;
    }

    static /* synthetic */ byte[] access$202(SportPlusHandle x0, byte[] x1) {
        x0.mSummary = x1;
        return x1;
    }

    static /* synthetic */ byte[] access$902(SportPlusHandle x0, byte[] x1) {
        x0.mDetails = x1;
        return x1;
    }

    public static interface IOpResult {
        public void onSummary(int var1, List<SportPlusEntity> var2);
    }
}

