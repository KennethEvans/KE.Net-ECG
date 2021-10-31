package net.kenevans.polar.polarecg;

public interface IConstants {
    String TAG = "KE.NetECG";

    String PREF_DEVICE_ID = "deviceId";
    String PREF_MRU_DEVICE_IDS = "mruDeviceIds";
    String PREF_TREE_URI = "treeUri";

    int REQ_ACCESS_LOCATION = 1;
    int REQ_ENABLE_BLUETOOTH = 2;
    int REQ_GET_TREE = 10;
}
