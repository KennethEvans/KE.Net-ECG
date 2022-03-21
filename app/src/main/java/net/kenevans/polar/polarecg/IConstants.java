package net.kenevans.polar.polarecg;

public interface IConstants {
    String TAG = "KE.NetECG";

    // Plotting
    /**
     * Maximum item age for real-time plot, in ms.
     */
    int PLOT_MAXIMUM_AGE = 300000;

    /**
     * Value for a database date value indicating invalid.
     */
    long INVALID_DATE = Long.MIN_VALUE;

    /**
     * Name of the package for this application.
     */
    String PACKAGE_NAME = "net.kenevans.polar.polarecg";

    String PREF_DEVICE_ID = "deviceId";
    String PREF_MRU_DEVICE_IDS = "mruDeviceIds";
    String PREF_TREE_URI = "treeUri";
    String PREF_PATIENT_NAME = "patientName";
    String PREF_QRS_VISIBILITY = "qrsVisibility";

    int REQ_ACCESS_PERMISSIONS = 1;
    int REQ_SETTINGS = 15;
//    int REQ_ENABLE_BLUETOOTH = 2;
//    int REQ_GET_TREE = 10;

    /**
     * Key for information URL sent to InfoActivity.
     */
    String INFO_URL = "InformationURL";

    /**
     * Intent code for showing settings.
     */
    String SETTINGS_CODE = PACKAGE_NAME + ".SettingsCode";
}
