package net.kenevans.polar.polarecg;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.XYPlot;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.polar.sdk.api.PolarBleApi;
import com.polar.sdk.api.PolarBleApiCallback;
import com.polar.sdk.api.PolarBleApiDefaultImpl;
import com.polar.sdk.api.errors.PolarInvalidArgument;
import com.polar.sdk.api.model.PolarDeviceInfo;
import com.polar.sdk.api.model.PolarEcgData;
import com.polar.sdk.api.model.PolarHrData;
import com.polar.sdk.api.model.PolarSensorSetting;

import org.reactivestreams.Publisher;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Function;

public class ECGActivity extends AppCompatActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        IConstants,
        IQRSConstants {
    public static final SimpleDateFormat sdfShort =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private SharedPreferences mSharedPreferences;
    private static final int MAX_DEVICES = 3;
    // Currently the sampling rate is fixed at 130
    private QRSDetection mQRS;
    List<DeviceInfo> mMruDevices;
    private XYPlot mECGPlot;
    private XYPlot mHRPlot;
    private XYPlot mQRSPlot;
    public ECGPlotter mECGPlotter;
    public HRPlotter mHRPlotter;
    public QRSPlotter mQRSPlotter;

    public boolean mOrientationChangedECG = false;
    public boolean mOrientationChangedQRS = false;
    // Not used when androidScreenOrientation="portrait"
//    public boolean mOrientationChangedHR = false;

    private boolean mBleSupported;
    private boolean mAllPermissionsAsked;

    private boolean mConnected = false;

    //    public boolean useQRSPlotter = true;
    public boolean mUseQRSPlot = false;

    /***
     * Whether to save as CSV, Plot, or both.
     */
    private enum SaveType {DATA, PLOT, BOTH, DEVICE_HR, QRS_HR, ALL}

    TextView mTextViewHR, mTextViewInfo, mTextViewTime;
    private PolarBleApi mApi;
    private Disposable mEcgDisposable;
    private boolean mPlaying;
    private Menu mMenu;
    private String mDeviceId = "";
    private String mFirmware = "NA";
    private String mName = "NA";
    private String mAddress = "NA";
    private String mBatteryLevel = "NA";
    //* Device HR when stopped playing. Updated whenever playing started or
    // stopped. */
    private String mDeviceStopHR = "NA";
    //* Calculated HR when stopped playing. Updated whenever playing started
    // or stopped. */
    private String mCalcStopHR = "NA";
    //* Date when stopped playing. Updated whenever playing started or
    // stopped. */
    private Date mStopTime;

    // Launcher for enabling Bluetooth
    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Log.d(TAG, "enableBluetoothLauncher: result" +
                                ".getResultCode()=" + result.getResultCode());
                        if (result.getResultCode() != RESULT_OK) {
                            Utils.warnMsg(this, "This app will not work with " +
                                    "Bluetooth disabled");
                        }
                    });

    // Launcher for PREF_TREE_URI
    private final ActivityResultLauncher<Intent> openDocumentTreeLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Log.d(TAG, "openDocumentTreeLauncher: result" +
                                ".getResultCode()=" + result.getResultCode());
                        // Find the UID for this application
                        Log.d(TAG, "URI=" + UriUtils.getApplicationUid(this));
                        Log.d(TAG,
                                "Current permissions (initial): "
                                        + UriUtils.getNPersistedPermissions(this));
                        try {
                            if (result.getResultCode() == RESULT_OK) {
                                // Get Uri from Storage Access Framework.
                                Uri treeUri = result.getData().getData();
                                SharedPreferences.Editor editor =
                                        getPreferences(MODE_PRIVATE)
                                                .edit();
                                if (treeUri == null) {
                                    editor.putString(PREF_TREE_URI, null);
                                    editor.apply();
                                    Utils.errMsg(this, "Failed to get " +
                                            "persistent " +
                                            "access permissions");
                                    return;
                                }
                                // Persist access permissions.
                                try {
                                    this.getContentResolver().takePersistableUriPermission(treeUri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                    // Save the current treeUri as PREF_TREE_URI
                                    editor.putString(PREF_TREE_URI,
                                            treeUri.toString());
                                    editor.apply();
                                    // Trim the persisted permissions
                                    UriUtils.trimPermissions(this, 1);
                                } catch (Exception ex) {
                                    String msg = "Failed to " +
                                            "takePersistableUriPermission for "
                                            + treeUri.getPath();
                                    Utils.excMsg(this, msg, ex);
                                }
                                Log.d(TAG,
                                        "Current permissions (final): "
                                                + UriUtils.getNPersistedPermissions(this));
                            }
                        } catch (Exception ex) {
                            Log.e(TAG, "Error in openDocumentTreeLauncher: " +
                                    "startActivity for result", ex);
                        }
                    });

    // Launcher for Settings
    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        String code = "Unknown";
                        if (result.getResultCode() == RESULT_OK) {
                            code = "RESULT_OK";
                        } else if (result.getResultCode() == RESULT_CANCELED) {
                            code = "RESULT_CANCELED";
                        }
                        // PREF_DEVICE_ID
                        String oldDeviceId = mDeviceId;
                        mDeviceId =
                                mSharedPreferences.getString(PREF_DEVICE_ID,
                                        "");
                        Log.d(TAG, "settingsLauncher: resultCode=" + code
                                + " oldDeviceId=" + oldDeviceId
                                + " mDeviceId=" + mDeviceId);
                        if (!oldDeviceId.equals(mDeviceId)) {
                            resetDeviceId(oldDeviceId);
                        }
                        // PREF_QRS_VISIBILITY
                        mUseQRSPlot = mSharedPreferences.getBoolean(
                                PREF_QRS_VISIBILITY, true);
                        setQRSVisibility();
                    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, this.getClass().getSimpleName() + " onCreate");
        super.onCreate(savedInstanceState);
        // Capture global exceptions
        Thread.setDefaultUncaughtExceptionHandler((paramThread,
                                                   paramThrowable) -> {
            Log.e(TAG, "Unexpected exception :", paramThrowable);
            // Any non-zero exit code
            System.exit(2);
        });

        setContentView(R.layout.activity_ecg);
        mTextViewHR = findViewById(R.id.hr);
        mTextViewInfo = findViewById(R.id.info);
        mTextViewTime = findViewById(R.id.time);
        mECGPlot = findViewById(R.id.ecgplot);
        mHRPlot = findViewById(R.id.hrplot);
        mQRSPlot = findViewById(R.id.qrsplot);

        // Make the QRS plot and it's layout Gone
        if (!mUseQRSPlot) {
            mQRSPlot.setVisibility(View.GONE);
        }

        mSharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this);
        // Register preference listener
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);

        setLastHr();
        mStopTime = new Date();

//        // DEBUG
//        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
//        Log.d(TAG, "All settings (getPreferences(MODE_PRIVATE)):\n"
//                + Utils.getSharedPreferencesInfo("    ", prefs));
//        prefs = PreferenceManager.getDefaultSharedPreferences(this);
//        Log.d(TAG, "All settings (getDefaultSharedPreferences):\n"
//                + Utils.getSharedPreferencesInfo("    ", prefs));

        // Start Bluetooth
        mDeviceId = mSharedPreferences.getString(PREF_DEVICE_ID, "");
        Log.d(TAG, "    mDeviceId=" + mDeviceId);
        Gson gson = new Gson();
        Type type = new TypeToken<LinkedList<DeviceInfo>>() {
        }.getType();
        String json = mSharedPreferences.getString(PREF_MRU_DEVICE_IDS, null);
        mMruDevices = gson.fromJson(json, type);
        if (mMruDevices == null) {
            mMruDevices = new ArrayList<>();
        }

        if (mDeviceId == null || mDeviceId.equals("")) {
            selectDeviceId();
        }

        // Use this check to determine whether BLE is supported on the device.
        // Then you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            String msg = getString(R.string.ble_not_supported);
            Utils.warnMsg(this, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            mBleSupported = false;
            return;
        } else {
            mBleSupported = true;
        }

        // Ask for needed permissions
        requestPermissions();
    }

    @Override
    protected void onPause() {
        Log.v(TAG, this.getClass().getSimpleName() + " onPause");
        super.onPause();

        if (mApi != null) mApi.backgroundEntered();
    }

    @Override
    public void onResume() {
        Log.d(TAG, this.getClass().getSimpleName() + " onResume:");
        super.onResume();

        // Check if PREF_TREE_URI is valid and remove it if not
        if (UriUtils.getNPersistedPermissions(this) <= 0) {
            SharedPreferences.Editor editor =
                    getPreferences(MODE_PRIVATE)
                            .edit();
            editor.putString(PREF_TREE_URI, null);
            editor.apply();
        }

        if (mApi != null) mApi.foregroundEntered();
        invalidateOptionsMenu();

        // Setup the plots if not done
//        Log.d(TAG,
//                "    mECGPlotter=" + mECGPlotter + " mHRPlotter=" + mHRPlotter
//                        + " mQRSPlotter=" + mQRSPlotter);
        if (mECGPlotter == null) {
            mECGPlot.post(() -> mECGPlotter =
                    new ECGPlotter(this, mECGPlot,
                            "ECG", Color.RED, false));
        }
        if (mHRPlotter == null) {
            mHRPlot.post(() -> mHRPlotter = new HRPlotter(this, mHRPlot));
        }
        if (mQRSPlotter == null) {
            mQRSPlot.post(() -> mQRSPlotter =
                    new QRSPlotter(this, mQRSPlot));
        }

        // Set the visibility of the QRS plot
        mUseQRSPlot = mSharedPreferences.getBoolean(PREF_QRS_VISIBILITY, true);
        setQRSVisibility();

        // Start the connection to the device
        Log.d(TAG, "    mDeviceId=" + mDeviceId);
        Log.d(TAG, "    mApi=" + mApi);
        mDeviceId = mSharedPreferences.getString(PREF_DEVICE_ID, "");
        if (mDeviceId == null || mDeviceId.isEmpty()) {
            Toast.makeText(this,
                    getString(R.string.noDevice),
                    Toast.LENGTH_SHORT).show();
        } else {
            restart();
        }
//        Log.d(TAG, "    onResume(end) mPlaying=" + mPlaying);
    }

    @Override
    public void onBackPressed() {
        // This seems to be necessary with Android 12
        // Otherwise onDestroy is not called
        Log.d(TAG, this.getClass().getSimpleName() + ": onBackPressed");
        finish();
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
//        Log.d(TAG, this.getClass().getSimpleName() + " onCreateOptionsMenu");
//        Log.d(TAG, "    mPlaying=" + mPlaying);
        mMenu = menu;
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        if (mApi == null) {
            mMenu.findItem(R.id.pause).setTitle("Start");
            mMenu.findItem(R.id.save).setVisible(false);
        } else if (mPlaying) {
            mMenu.findItem(R.id.pause).setIcon(ResourcesCompat.
                    getDrawable(getResources(),
                            R.drawable.ic_stop_white_36dp, null));
            mMenu.findItem(R.id.pause).setTitle("Pause");
            mMenu.findItem(R.id.save).setVisible(false);
        } else {
            mMenu.findItem(R.id.pause).setIcon(ResourcesCompat.
                    getDrawable(getResources(),
                            R.drawable.ic_play_arrow_white_36dp, null));
            mMenu.findItem(R.id.pause).setTitle("Start");
            mMenu.findItem(R.id.save).setVisible(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.pause) {
            if (mApi == null) {
                return true;
            }
            if (mPlaying) {
                // Turn it off
                setLastHr();
                mStopTime = new Date();
                mPlaying = false;
                setPanBehavior();
                if (mEcgDisposable != null) {
                    // Turns it off
                    streamECG();
                }
                mMenu.findItem(R.id.pause).setIcon(ResourcesCompat.
                        getDrawable(getResources(),
                                R.drawable.ic_play_arrow_white_36dp, null));
                mMenu.findItem(R.id.pause).setTitle("Start");
                mMenu.findItem(R.id.save).setVisible(true);
            } else {
                // Turn it on
                setLastHr();
                mStopTime = new Date();
                mPlaying = true;
                setPanBehavior();
                mTextViewTime.setText(getString(R.string.elapsed_time,
                        0.0));
                // Clear the plot
                mECGPlotter.clear();
                mQRSPlotter.clear();
                mHRPlotter.clear();
                if (mEcgDisposable == null) {
                    // Turns it on
                    streamECG();
                }
                mMenu.findItem(R.id.pause).setIcon(ResourcesCompat.
                        getDrawable(getResources(),
                                R.drawable.ic_stop_white_36dp, null));
                mMenu.findItem(R.id.pause).setTitle("Pause");
                mMenu.findItem(R.id.save).setVisible(false);
            }
            return true;
        } else if (id == R.id.save_plot) {
            saveDataWithNote(SaveType.PLOT);
            return true;
        } else if (id == R.id.save_data) {
            saveDataWithNote(SaveType.DATA);
            return true;
        } else if (id == R.id.save_both) {
            saveDataWithNote(SaveType.BOTH);
            return true;
        } else if (id == R.id.save_all) {
            saveDataWithNote(SaveType.ALL);
            return true;
        } else if (id == R.id.save_device_data) {
            doSaveSessionData(SaveType.DEVICE_HR);
            return true;
        } else if (id == R.id.save_qrs_data) {
            doSaveSessionData(SaveType.QRS_HR);
            return true;
        } else if (id == R.id.info) {
            info();
            return true;
        } else if (id == R.id.restart_api) {
            restartApi();
            return true;
        } else if (id == R.id.redo_plot_setup) {
            redoPlotSetup();
            return true;
        } else if (id == R.id.device_id) {
            selectDeviceId();
            return true;
        } else if (id == R.id.choose_data_directory) {
            chooseDataDirectory();
            return true;
        } else if (id == R.id.help) {
            showHelp();
            return true;
        } else if (item.getItemId() == R.id.menu_settings) {
            showSettings();
            return true;
        }
        return false;
    }

    /**
     * This is necessary to handle orientation changes and keep the plot. It
     * needs<br><br>
     * android:configChanges="orientation|keyboardHidden|screenSize"<br><br>
     * in AndroidManifest for the Activity.  With this set onPause and
     * onResume are not called, only this.  Otherwise orientation changes
     * cause it to start over with onCreate.  <br><br>
     *
     * @param newConfig The new configuration.
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Log.d(TAG, this.getClass().getSimpleName() +
                " onConfigurationChanged: "
                + (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ?
                "Landscape" : "Portrait")
                + " time=" + sdfShort.format(new Date())
        );
        super.onConfigurationChanged(newConfig);
/*
        The following is when handling orientation change
        Not used for anddoidScreenOrientation="portrait"
        mOrientationChangedECG = mOrientationChangedQRS =
                mOrientationChangedHR = true;

        TextView hrOld = mTextViewHR;
        TextView timeOld = mTextViewTime;
        TextView infoOld = mTextViewInfo;

        // At this point the content view has not been changed. Change it.
        // It will use the one appropriate to the new orientation.
        setContentView(R.layout.activity_ecg);

        // At this point the new layouts should be correct but they have
        // undefined Views.
        mECGPlot = findViewById(R.id.ecgplot);
        mQRSPlot = findViewById(R.id.qrsplot);
        if (!mUseQRSPlot) {
            mQRSPlot.setVisibility(View.GONE);
        }
        mHRPlot = findViewById(R.id.hrplot);
        mTextViewHR = findViewById(R.id.hr);
        mTextViewTime = findViewById(R.id.time);
        mTextViewInfo = findViewById(R.id.info);

        // Put the old information into the new views
        // Layout ecg
        mECGPlot.post(() -> {
            mTextViewHR.setText(hrOld.getText());
            mTextViewTime.setText(timeOld.getText());
            mTextViewInfo.setText(infoOld.getText());
//            Log.d(TAG, "mECGPlot.post (before): time="
//                    + sdfShort.format(new Date())
//                    + " plotter=" + Utils.getHashCode(mECGPlotter)
//                    + " plot=" + Utils.getHashCode(mECGPlot)
//                    + " isLaidOut=" + mECGPlot.isLaidOut()
//            );
            mECGPlotter = mECGPlotter.getNewInstance(mECGPlot);
            mECGPlotter.setPanning(!mPlaying);
            mOrientationChangedECG = false;
//            Log.d(TAG, "mECGPlot.post (after): time="
//                    + sdfShort.format(new Date())
//                    + " plotter=" + Utils.getHashCode(mECGPlotter)
//                    + " plot=" + Utils.getHashCode(mECGPlot)
        });

        // Layout analysis
        mQRSPlot.post(() -> {
//            Log.d(TAG, "mQRSPlot.post (before): time="
//                    + sdfShort.format(new Date())
//                    + " plotter=" + Utils.getHashCode(mQRSPlotter)
//                    + " plot=" + Utils.getHashCode(mQRSPlot)
//                    + " isLaidOut=" + mQRSPlot.isLaidOut()
//            );
            mQRSPlotter = mQRSPlotter.getNewInstance(mQRSPlot);
            mQRSPlotter.setPanning(!mPlaying);
            mOrientationChangedQRS = false;
//            Log.d(TAG, "mQRSPlot.post (after): time="
//                    + sdfShort.format(new Date())
//                    + " plotter=" + Utils.getHashCode(mQRSPlotter)
//                    + " plot=" + Utils.getHashCode(mQRSPlot)
//            );
        });
        mHRPlot.post(() -> {
//            Log.d(TAG, "mHRPlot.post (before): time="
//                    + sdfShort.format(new Date())
//                    + " plotter=" + Utils.getHashCode(mHRPlotter)
//                    + " plot=" + Utils.getHashCode(mHRPlot)
//                    + " isLaidOut=" + mHRPlot.isLaidOut()
//            );
            mHRPlotter = mHRPlotter.getNewInstance(mHRPlot);
            mOrientationChangedHR = false;
//            Log.d(TAG, "mHRPlot.post (after): time="
//                    + sdfShort.format(new Date())
//                    + " plotter=" + Utils.getHashCode(mHRPlotter)
//                    + " plot=" + Utils.getHashCode(mHRPlot)
//            );
        });
 */
    }

    /**
     * Get the resource id for the view.
     *
     * @param view The View.
     * @return The id.
     */
    public String getViewId(View view) {
        return getResources().getResourceEntryName(view.getId());
    }

    /**
     * Get info for a view, including the id, name, and hash code.
     *
     * @param view The view.
     * @param tag  User-supplied tag, preferably <= 8 characters.
     * @return The info.
     */
    @SuppressWarnings("unused")
    public String getViewInfo(View view, String tag) {
        String hash = Utils.getHashCode(view);
        String id = getViewId(view);
        String name = view.getClass().getSimpleName();
        return String.format("%-8s %-8s %-20s %8s", id, tag, name, hash);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, this.getClass().getSimpleName() + " onDestroy");
        super.onDestroy();
        if (mApi != null) mApi.shutDown();
        // Remove preference listener
        if (mSharedPreferences != null) {
            mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[]
            permissions, @NonNull int[] grantResults) {
        Log.d(TAG, this.getClass().getSimpleName()
                + "onRequestPermissionsResult");
        super.onRequestPermissionsResult(requestCode, permissions,
                grantResults);
        if (requestCode == REQ_ACCESS_PERMISSIONS) {// All (Handle multiple)
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.
                        permission.ACCESS_COARSE_LOCATION)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: COARSE_LOCATION " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: COARSE_LOCATION " +
                                "denied");
                    }
                } else if (permissions[i].equals(Manifest.
                        permission.ACCESS_FINE_LOCATION)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: FINE_LOCATION " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: FINE_LOCATION " +
                                "denied");
                    }
                } else if (permissions[i].equals(Manifest.
                        permission.BLUETOOTH_SCAN)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: BLUETOOTH_SCAN " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: BLUETOOTH_SCAN " +
                                "denied");
                    }
                } else if (permissions[i].equals(Manifest.
                        permission.BLUETOOTH_CONNECT)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: BLUETOOTH_CONNECT" +
                                " " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_PERMISSIONS: BLUETOOTH_CONNECT" +
                                " " +
                                "denied");
                    }
                }
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        Log.d(TAG, "onSharedPreferenceChanged: key=" + key);
    }

    /***
     * Sets the last HR from the series in the HR plotter.
     */
    public void setLastHr() {
        mDeviceStopHR = mCalcStopHR = "NA";
        if (mHRPlotter == null) return;
        long lastVal;
        if (mHRPlotter.mHrSeries1 != null && mHRPlotter.mHrSeries1.size() > 0) {
            lastVal = Math.round(
                    mHRPlotter.mHrSeries1.getyVals().getLast().doubleValue());
            mDeviceStopHR = String.format(Locale.US, "%d", lastVal);
        }
        if (mHRPlotter.mHrSeries2 != null && mHRPlotter.mHrSeries2.size() > 0) {
            lastVal = Math.round(
                    mHRPlotter.mHrSeries2.getyVals().getLast().doubleValue());
            mCalcStopHR = String.format(Locale.US, "%d", lastVal);
        }
    }

    /**
     * Show the help.
     */
    public void showHelp() {
        Log.d(TAG, "showHelp");
        try {
            // Start theInfoActivity
            Intent intent = new Intent();
            intent.setClass(this, InfoActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra(INFO_URL, "file:///android_asset/kedotnetecg.html");
            startActivity(intent);
        } catch (Exception ex) {
            Utils.excMsg(this, getString(R.string.help_show_error), ex);
        }
    }

    /**
     * Calls the settings activity.
     */
    public void showSettings() {
        Log.d(TAG, "showSettings");
        Intent intent = new Intent(ECGActivity.this,
                SettingsActivity.class);
        settingsLauncher.launch(intent);
    }

    public void selectDeviceId() {
        if (mMruDevices.size() == 0) {
            showDeviceIdDialog(null);
            return;
        }
        final AlertDialog.Builder[] dialog =
                {new AlertDialog.Builder(ECGActivity.this, R.style.PolarTheme)};
        dialog[0].setTitle(R.string.device_id_item);
        String[] items = new String[mMruDevices.size() + 1];
        DeviceInfo deviceInfo;
        for (int i = 0; i < mMruDevices.size(); i++) {
            deviceInfo = mMruDevices.get(i);
            items[i] = deviceInfo.name;
        }
        items[mMruDevices.size()] = "New";
        int checkedItem = 0;
        dialog[0].setSingleChoiceItems(items, checkedItem,
                (dialogInterface, which) -> {
                    if (which < mMruDevices.size()) {
                        DeviceInfo deviceInfo1 = mMruDevices.get(which);
                        String oldDeviceId = mDeviceId;
                        setDeviceMruPref(deviceInfo1);
                        Log.d(TAG, "which=" + which
                                + " name=" + deviceInfo1.name + " id="
                                + deviceInfo1.id);
                        Log.d(TAG,
                                "selectDeviceId: oldDeviceId=" + oldDeviceId
                                        + " mDeviceId=" + mDeviceId);
                        if (!oldDeviceId.equals(mDeviceId)) {
                            resetDeviceId(oldDeviceId);
                        }
                    } else {
                        showDeviceIdDialog(null);
                    }
                    dialogInterface.dismiss();
                });
        dialog[0].setNegativeButton(R.string.cancel,
                (dialog1, which) -> dialog1.dismiss());
        AlertDialog alert = dialog[0].create();
        alert.setCanceledOnTouchOutside(false);
        alert.show();
    }

    public void showDeviceIdDialog(View view) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this,
                R.style.PolarTheme);
        dialog.setTitle(R.string.device_id_item);

        View viewInflated = LayoutInflater.from(getApplicationContext()).
                inflate(R.layout.device_id_dialog_layout,
                        view == null ? null : (ViewGroup) view.getRootView(),
                        false);

        final EditText input = viewInflated.findViewById(R.id.input);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        mDeviceId = mSharedPreferences.getString(PREF_DEVICE_ID, "");
        input.setText(mDeviceId);
        dialog.setView(viewInflated);

        dialog.setPositiveButton(R.string.ok,
                (dialogInterface, which) -> {
                    String oldDeviceId = mDeviceId;
                    mDeviceId = input.getText().toString();
                    Log.d(TAG, "showDeviceIdDialog: OK:  oldDeviceId="
                            + oldDeviceId + " newDeviceId="
                            + mDeviceId);
                    SharedPreferences.Editor editor =
                            mSharedPreferences.edit();
                    editor.putString(PREF_DEVICE_ID, mDeviceId);
                    editor.apply();
                    if (mDeviceId == null || mDeviceId.isEmpty()) {
                        Toast.makeText(ECGActivity.this,
                                getString(R.string.noDevice),
                                Toast.LENGTH_SHORT).show();
                    } else if (!oldDeviceId.equals(mDeviceId)) {
                        resetDeviceId(oldDeviceId);
                    }
                });
        dialog.setNegativeButton(R.string.cancel,
                (dialog1, which) -> {
                    Log.d(TAG,
                            "showDeviceIdDialog: Cancel:  mDeviceId=" + mDeviceId);
                    dialog1.cancel();
                    if (mDeviceId == null || mDeviceId.isEmpty()) {
                        Toast.makeText(ECGActivity.this,
                                getString(R.string.noDevice),
                                Toast.LENGTH_SHORT).show();
                    }
                });
        dialog.show();
    }

    public void setDeviceMruPref(DeviceInfo deviceInfo) {
        SharedPreferences.Editor editor =
                mSharedPreferences.edit();
        // Remove any found so the new one will be added at the beginning
        List<DeviceInfo> removeList = new ArrayList<>();
        for (DeviceInfo deviceInfo1 : mMruDevices) {
            if (deviceInfo.name.equals(deviceInfo1.name) &&
                    deviceInfo.id.equals(deviceInfo1.id)) {
                removeList.add(deviceInfo1);
            }
        }
        for (DeviceInfo deviceInfo1 : removeList) {
            mMruDevices.remove(deviceInfo1);
        }
        // Remove at end if size exceed max
        if (mMruDevices.size() != 0 && mMruDevices.size() == MAX_DEVICES) {
            mMruDevices.remove(mMruDevices.size() - 1);
        }
        // Add at the beginning
        mMruDevices.add(0, deviceInfo);
        Gson gson = new Gson();
        String json = gson.toJson(mMruDevices);
        editor.putString(PREF_MRU_DEVICE_IDS, json);
        mDeviceId = deviceInfo.id;
        editor.putString(PREF_DEVICE_ID, deviceInfo.id);
        editor.apply();
    }

    /**
     * Panning while collecting data causes problems. Turn it off when
     * playing and turn it on with stopped. Zooming is not enabled.
     */
    private void setPanBehavior() {
        mECGPlotter.setPanning(!mPlaying);
        mQRSPlotter.setPanning(!mPlaying);
//        mHRPlotter.setPanning(!mPlaying);
    }

    /**
     * Save the current samples as a file.  Prompts for a note, then calls
     * the appropriate doSave method.
     *
     * @param saveType The SaveType.
     */
    private void saveDataWithNote(final SaveType saveType) {
        String msg;
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            msg = "External Storage is not available";
            Log.e(TAG, msg);
            Utils.errMsg(this, msg);
            return;
        }
        // Get a note
        AlertDialog.Builder dialog = new AlertDialog.Builder(this,
                R.style.PolarTheme);
        dialog.setTitle(R.string.note_dialog_title);

        View viewInflated = LayoutInflater.from(getApplicationContext()).
                inflate(R.layout.device_id_dialog_layout, null, false);

        final EditText input = viewInflated.findViewById(R.id.input);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        dialog.setView(viewInflated);

        dialog.setPositiveButton(R.string.ok,
                (dialog1, which) -> {
                    switch (saveType) {
                        case DATA:
                            doSaveData(input.getText().toString());
                            break;
                        case PLOT:
                            doSavePlot(input.getText().toString());
                            break;
                        case BOTH:
                            doSaveData(input.getText().toString());
                            doSavePlot(input.getText().toString());
                            break;
                        case ALL:
                            doSaveData(input.getText().toString());
                            doSavePlot(input.getText().toString());
                            doSaveSessionData(SaveType.DEVICE_HR);
                            doSaveSessionData(SaveType.QRS_HR);
                            break;
                        case DEVICE_HR:
                        case QRS_HR:
                            doSaveSessionData(saveType);
                            break;
                    }
                });
        dialog.setNegativeButton(R.string.cancel,
                (dialog12, which) -> {
                });
        dialog.show();
    }

    /**
     * Finishes the savePlot after getting the note.
     *
     * @param note The note.
     */
    private void doSavePlot(final String note) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no data directory set");
            return;
        }
        String patientName = prefs.getString(PREF_PATIENT_NAME, "");
        String msg;
        String format = "yyyy-MM-dd_HH-mm";
        SimpleDateFormat df = new SimpleDateFormat(format, Locale.US);
        String fileName = "PolarECG-" + df.format(mStopTime) + ".png";
        try {
            Uri treeUri = Uri.parse(treeUriStr);
            String treeDocumentId =
                    DocumentsContract.getTreeDocumentId(treeUri);
            Uri docTreeUri =
                    DocumentsContract.buildDocumentUriUsingTree(treeUri,
                            treeDocumentId);
            ContentResolver resolver = this.getContentResolver();
            ParcelFileDescriptor pfd;
            Uri docUri = DocumentsContract.createDocument(resolver, docTreeUri,
                    "image/png", fileName);
            pfd = getContentResolver().
                    openFileDescriptor(docUri, "w");
            try (FileOutputStream strm =
                         new FileOutputStream(pfd.getFileDescriptor())) {
                Bitmap logo = BitmapFactory.decodeResource(this.getResources(),
                        R.drawable.polar_ecg);
                PlotArrays arrays = getPlotArrays();
                final double[] ecgvals = arrays.ecg;
                final boolean[] peakvals = arrays.peaks;
                mECGPlotter.getSeries().getyVals();
                final int nSamples = ecgvals.length;
                Bitmap bm = EcgImage.createImage(FS, logo,
                        patientName,
                        mStopTime.toString(),
                        mDeviceId,
                        mFirmware,
                        mBatteryLevel,
                        note,
                        mDeviceStopHR,
                        mCalcStopHR,
                        String.format(Locale.US, "%d",
                                mQRSPlotter.mSeries4.size()),
                        String.format(Locale.US, "%.1f sec", nSamples / FS),
                        ecgvals,
                        peakvals);
                bm.compress(Bitmap.CompressFormat.PNG, 80, strm);
                strm.close();
                msg = "Wrote " + docUri.getLastPathSegment();
                Log.d(TAG, msg);
                Utils.infoMsg(this, msg);
            }
        } catch (Exception ex) {
            msg = "Error saving plot";
            Log.e(TAG, msg);
            Log.e(TAG, Log.getStackTraceString(ex));
            Utils.excMsg(this, msg, ex);
        }
    }

    /**
     * Finishes the saveData after getting the note.
     *
     * @param note The note.
     */
    private void doSaveData(String note) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no data directory set");
            return;
        }
        String msg;
        String format = "yyyy-MM-dd_HH-mm";
        SimpleDateFormat df = new SimpleDateFormat(format, Locale.US);
        String fileName = "PolarECG-" + df.format(mStopTime) + ".csv";
        try {
            Uri treeUri = Uri.parse(treeUriStr);
            String treeDocumentId =
                    DocumentsContract.getTreeDocumentId(treeUri);
            Uri docTreeUri =
                    DocumentsContract.buildDocumentUriUsingTree(treeUri,
                            treeDocumentId);
            ContentResolver resolver = this.getContentResolver();
            ParcelFileDescriptor pfd;
            Uri docUri = DocumentsContract.createDocument(resolver, docTreeUri,
                    "text/csv", fileName);
            pfd = getContentResolver().
                    openFileDescriptor(docUri, "w");
            try (FileWriter writer = new FileWriter(pfd.getFileDescriptor());
                 PrintWriter out = new PrintWriter((writer))) {
                // Write header
                PlotArrays arrays = getPlotArrays();
                final double[] ecgvals = arrays.ecg;
                final boolean[] peakvals = arrays.peaks;
                int nPeaks = mQRSPlotter.mSeries4.size();
                int nSamples = ecgvals.length;
                String duration = String.format(Locale.US, "%.1f sec",
                        nSamples / FS);
                out.write("application=" + "KE.Net ECG Version: "
                        + Utils.getVersion(this) + "\n");
                out.write("stoptime=" + mStopTime.toString() + "\n");
                out.write("duration=" + duration + "\n");
                out.write("nsamples=" + nSamples + "\n");
                out.write("samplingrate=" + FS + "\n");
                out.write("stopdevicehr=" + mDeviceStopHR + "\n");
                out.write("stopcalculatedhr=" + mCalcStopHR + "\n");
                out.write("npeaks=" + nPeaks + "\n");
                out.write("devicename=" + mName + "\n");
                out.write("deviceid=" + mDeviceId + "\n");
                out.write("battery=" + mBatteryLevel + "\n");
                out.write("firmware=" + mFirmware + "\n");
                out.write("note=" + note + "\n");

                // Write samples
                for (int i = 0; i < nSamples; i++) {
                    out.write(String.format(Locale.US, "%.3f,%d\n", ecgvals[i],
                            peakvals[i] ? 1 : 0));
                }
                out.flush();
                msg = "Wrote " + docUri.getLastPathSegment();
                Log.d(TAG, msg);
                Utils.infoMsg(this, msg);
            }
        } catch (Exception ex) {
            msg = "Error writing CSV file";
            Log.e(TAG, msg);
            Log.e(TAG, Log.getStackTraceString(ex));
            Utils.excMsg(this, msg, ex);
        }
    }

    /**
     * Finishes the saveData.
     *
     * @param saveType The saveType (either DEVICE_HR or QRS_HR).
     */
    private void doSaveSessionData(SaveType saveType) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no data directory set");
            return;
        }
        String msg;
        String format = "yyyy-MM-dd_HH-mm";
        SimpleDateFormat df = new SimpleDateFormat(format, Locale.US);
        String fileName;
        List<HRPlotter.HrRrSessionData> dataList;
        try {
            if (saveType == SaveType.DEVICE_HR) {
                fileName = "PolarECG-DeviceHR-" + df.format(mStopTime) + ".csv";
                dataList = mHRPlotter.mHrRrList1;
            } else if (saveType == SaveType.QRS_HR) {
                fileName = "PolarECG-QRSHR-" + df.format(mStopTime) + ".csv";
                dataList = mHRPlotter.mHrRrList2;
            } else {
                Utils.errMsg(this,
                        "Invalid saveType (" + saveType + "0 in " +
                                "doSaveSessionData");
                return;
            }

            Uri treeUri = Uri.parse(treeUriStr);
            String treeDocumentId =
                    DocumentsContract.getTreeDocumentId(treeUri);
            Uri docTreeUri =
                    DocumentsContract.buildDocumentUriUsingTree(treeUri,
                            treeDocumentId);
            ContentResolver resolver = this.getContentResolver();
            ParcelFileDescriptor pfd;
            Uri docUri = DocumentsContract.createDocument(resolver, docTreeUri,
                    "text/csv", fileName);
            pfd = getContentResolver().
                    openFileDescriptor(docUri, "w");
            try (FileWriter writer = new FileWriter(pfd.getFileDescriptor());
                 PrintWriter out = new PrintWriter((writer))) {
                // Write header (None)
                // Write samples
                for (HRPlotter.HrRrSessionData data : dataList) {
                    out.write(data.getCVSString() + "\n");
                }
                out.flush();
                msg = "Wrote " + docUri.getLastPathSegment();
                Utils.infoMsg(this, msg);
                Log.d(TAG, "    Wrote " + dataList.size() + " items");
            }
        } catch (Exception ex) {
            msg = "Error writing " + saveType + " CSV file";
            Log.e(TAG, msg);
            Log.e(TAG, Log.getStackTraceString(ex));
            Utils.excMsg(this, msg, ex);
        }
    }

    /**
     * Gets ECG and Peaks and converts them into doubles. This relies on the
     * ecg arrays in ECGPlotter and QRSPlotter being the same and also the
     * respective mDataIndex's. The ones in QRSPlotter are used. It also has
     * the peak values.
     *
     * @return PlotArrays with the ECG and peak values.
     */
    public PlotArrays getPlotArrays() {
        PlotArrays arrays;
        // Remove any out-of-range values peak values
        mQRSPlotter.removeOutOfRangeValues();
        LinkedList<Number> ecgvals = mQRSPlotter.mSeries1.getyVals();
        LinkedList<Number> peakvals = mQRSPlotter.mSeries4.getyVals();
        LinkedList<Number> peakxvals = mQRSPlotter.mSeries4.getxVals();
        int ecglen = ecgvals.size();
        int peakslen = peakvals.size();

        double[] ecg = new double[ecglen];
        boolean[] peaks = new boolean[ecglen];
        int i = 0;
        for (Number val : ecgvals) {
            ecg[i] = val.doubleValue();
            peaks[i] = false;
            i++;
        }
        // The peak values correspond to an index related to when they came in.
        // This is different from the index in the arrays, which goes from 0
        // to no more than N_TOTAL_POINTS.
        int offset = 0;
        if (mQRSPlotter.mDataIndex > N_TOTAL_POINTS) {
            offset = -(int) (mQRSPlotter.mDataIndex - N_TOTAL_POINTS);
        }
        int indx;
        for (int j = 0; j < peakslen; j++) {
            indx = peakxvals.get(j).intValue() + offset;
//            Log.d(TAG, String.format("j=%d indx=%d xval=%d",
//                    j, indx, peakxvals.get(j).intValue()));
            peaks[indx] = true;
        }
        arrays = new PlotArrays(ecg, peaks);
        return arrays;
    }

    public void redoPlotSetup() {
        if (mECGPlotter != null) {
            mECGPlotter.setupPlot();
        }
        if (mQRSPlotter != null) {
            mQRSPlotter.setupPlot();
        }
        if (mHRPlotter != null) {
            mHRPlotter.setupPlot();
        }
    }

    public void info() {
        StringBuilder msg = new StringBuilder();
        msg.append("Name: ").append(mName).append("\n");
        msg.append("Device Id: ").append(mDeviceId).append("\n");
        msg.append("Address: ").append(mAddress).append("\n");
        msg.append("Firmware: ").append(mFirmware).append("\n");
        msg.append("Battery Level: ").append(mBatteryLevel).append("\n");
        msg.append("API Connected: ").append(mApi != null).append("\n");
        msg.append("Device Connected: ").append(mConnected).append("\n");
        msg.append("Playing: ").append(mPlaying).append("\n");
        msg.append("Receiving ECG: ").append(mEcgDisposable != null)
                .append("\n");
        if (mECGPlotter != null && mECGPlotter.getSeries() !=
                null && mECGPlotter.getSeries().getyVals() != null) {
            double elapsed = mECGPlotter.getDataIndex() / FS;
            msg.append("Elapsed Time: ")
                    .append(getString(R.string.elapsed_time, elapsed))
                    .append("\n");
            msg.append("Points plotted: ")
                    .append(mECGPlotter.getSeries().getyVals().size())
                    .append("\n");
        }
        String versionName = "NA";
        try {
            versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;

        } catch (Exception ex) {
            // Do nothing
        }
        msg.append("KE.Net ECG Version: ").
                append(versionName).append("\n");
        msg.append("Polar BLE API Version: ").
                append(PolarBleApiDefaultImpl.versionInfo()).append("\n");
        msg.append(UriUtils.getRequestedPermissionsInfo(this));
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            msg.append("Data Directory: Not set");
        } else {
            Uri treeUri = Uri.parse(treeUriStr);
            msg.append("Data Directory: ").append(treeUri.getPath());
        }
        Utils.infoMsg(this, msg.toString());
    }

    /**
     * Toggles streaming for ECG.
     */
    public void streamECG() {
        Log.d(TAG, this.getClass().getSimpleName() + " streamECG:"
                + " mEcgDisposable=" + mEcgDisposable
                + " mConnected=" + mConnected
        );
        if (!mConnected) {
            Utils.errMsg(this, "streamECG: Device is not connected yet");
            return;
        }
        logEpochInfo("UTC");
        if (mEcgDisposable == null) {
            // Set the local time to get correct timestamps. H10 apparently
            // resets its time to 01:01:2019 00:00:00 when connected to strap
            TimeZone timeZone = TimeZone.getTimeZone("UTC");
            Calendar calNow = Calendar.getInstance(timeZone);
            Log.d(TAG, "setLocalTime to " + calNow.getTime());
            mApi.setLocalTime(mDeviceId, calNow);
            mEcgDisposable =
                    mApi.setLocalTime(mDeviceId, calNow)
                            .andThen(
                                    mApi.requestStreamSettings(mDeviceId,
                                            PolarBleApi.DeviceStreamingFeature.ECG))
                            .toFlowable()
                            .flatMap((Function<PolarSensorSetting,
                                    Publisher<PolarEcgData>>) sensorSetting -> {
//                                logSensorSettings(sensorSetting);
                                return mApi.startEcgStreaming(mDeviceId,
                                        sensorSetting.maxSettings());
                            }).observeOn(AndroidSchedulers.mainThread())
                            .subscribe(polarEcgData -> {
//                                        logTimestampInfo(polarEcgData);
                                        if (mQRS == null) {
                                            mQRS = new QRSDetection(ECGActivity.this);
                                        }
//                                        logEcgDataInfo(polarEcgData);
                                        mQRS.process(polarEcgData);
                                        // Update the elapsed time
                                        double elapsed =
                                                mECGPlotter.getDataIndex() / 130.;
                                        mTextViewTime.setText(getString(R.string.elapsed_time, elapsed));
                                    },
                                    throwable -> {
                                        Log.e(TAG,
                                                "ECG Error: "
                                                        + throwable.getLocalizedMessage(),
                                                throwable);
                                        Utils.excMsg(ECGActivity.this, "ECG " +
                                                        "Error: ",
                                                throwable);
                                        mEcgDisposable = null;
                                    },
                                    () -> Log.d(TAG, "ECG streaming complete")
                            );
        } else {
            // NOTE stops streaming if it is "running"
            mEcgDisposable.dispose();
            mEcgDisposable = null;
            if (mQRS != null) mQRS = null;
        }
    }

    public void logEpochInfo(String tzString) {
        SimpleDateFormat sdf =
                new SimpleDateFormat("dd:MM:yyyy HH:mm:ss", Locale.US);
        SimpleDateFormat sdf2 =
                new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US);
        /// Set the timezone
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        sdf2.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Date epoch = sdf.parse("01:01:2000 00:00:00");
            Date epoch1 = sdf.parse("01:01:2019 00:00:00");
            if (epoch != null && epoch1 != null) {
                Log.d(TAG, "epoch=" + sdf2.format(epoch)
                        + " epoch1=" + sdf2.format(epoch1)
                        + " " + epoch.getTime()
                        + " " + epoch1.getTime()
                        + " " + (epoch1.getTime() - epoch.getTime())
                        + tzString);
            } else {
                Log.d(TAG, "epoch=null and/or epoch2=null");
            }
        } catch (
                Exception ex) {
            Log.e(TAG, "Error parsing date", ex);
        }
    }

    public static double msToYears(long longVal) {
        return longVal / (1000. * 60. * 60. * 24. * 365.);
    }

    public static String timestampInfo(long ts, String name) {
        SimpleDateFormat sdf =
                new SimpleDateFormat("MMM dd yyyy HH:mm:ss zzz", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat sdf1 =
                new SimpleDateFormat("MMM dd yyyy HH:mm:ss zzz", Locale.US);
        Date date = new Date(ts);
        return String.format(Locale.US, "%15d %-8s %.2f years  %s %s",
                ts, name, msToYears(ts),
                sdf.format(date), sdf1.format(date));
    }

    @SuppressWarnings("unused")
    public static void logTimestampInfo(PolarEcgData polarEcgData) {
        // epoch to Polar
        long offset0 = 946684800000L;
        // Polar to default
        long offset1 = 599616000000L;
        long ts =
                polarEcgData.timeStamp /
                        1000000;
        long ts1 = ts + offset0;
        long now = new Date().getTime();
        Log.d(TAG, timestampInfo(offset1,
                "offset1"));
        Log.d(TAG, timestampInfo(ts, "ts"));
        Log.d(TAG, timestampInfo(ts1, "ts1"));
        Log.d(TAG, timestampInfo(now, "now"));
    }

    @SuppressWarnings("unused")
    public void logSensorSettings(PolarSensorSetting sensorSetting) {
        Log.d(TAG, mName + " PolarEcgData SensorSetting: "
                + " sampleRate=" + sensorSetting.maxSettings().settings
                .get(PolarSensorSetting.SettingType.SAMPLE_RATE)
                + " resolution = " + sensorSetting.maxSettings().settings
                .get(PolarSensorSetting.SettingType.RESOLUTION)
                + " range =" + sensorSetting.maxSettings().settings
                .get(PolarSensorSetting.SettingType.RANGE));
    }

    @SuppressWarnings("unused")
    public static void logEcgDataInfo(PolarEcgData polarEcgData) {
        SimpleDateFormat sdf1 =
                new SimpleDateFormat("MMM dd yyyy HH:mm:ss zzz", Locale.US);
        // epoch to Polar
        long offset0 = 946684800000L;
        long ts = polarEcgData.timeStamp / 1000000 + offset0;
        Date date = new Date(ts);
        Log.d(TAG, "PolarEcgData info: "
                + " thread=" + Thread.currentThread().getName()
                + " nSamples=" + polarEcgData.samples.size()
                + " timestamp="
                + sdf1.format(date) + " (" + ts + ")");
    }

    /**
     * Determines if either COARSE or FINE location permission is granted.
     *
     * @return If granted.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isAllPermissionsGranted(Context ctx) {
        boolean granted;
        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12 (S)
            granted = ctx.checkSelfPermission(
                    Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED |
                    ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                            PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 6 (M)
            granted = ctx.checkSelfPermission(
                    Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED |
                    ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED;
        }
        return granted;
    }

    public void requestPermissions() {
        Log.d(TAG, "requestPermissions");
        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();
            if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent =
                        new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                enableBluetoothLauncher.launch(enableBtIntent);
            }
        }

        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12 (S)
            this.requestPermissions(new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT},
                    REQ_ACCESS_PERMISSIONS);
        } else {
            // Android 6 (M)
            this.requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_ACCESS_PERMISSIONS);
        }
    }

    /**
     * Brings up a system file chooser to get the data directory
     */
    private void chooseDataDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION &
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        openDocumentTreeLauncher.launch(intent);
    }

    /**
     * Tries to disconnect mDeviceId from the current API and restarts the API.
     */
    private void restartApi() {
        Log.d(TAG, this.getClass().getSimpleName() + " restartApi:");
        resetDeviceId(mDeviceId);
    }

    /**
     * Tries to disconnect the device from the current API and restarts the API,
     * which will use the current mDeviceId.
     *
     * @param oldDeviceId The previous deviceId.
     */
    private void resetDeviceId(String oldDeviceId) {
        Log.d(TAG, this.getClass().getSimpleName() + " resetDeviceId:");
        if (mApi != null) {
            if (mEcgDisposable != null) {
                mEcgDisposable.dispose();
                mEcgDisposable = null;
            }
            try {
                mApi.disconnectFromDevice(oldDeviceId);
            } catch (PolarInvalidArgument ex) {
                String msg = "oldDeviceId=" + oldDeviceId
                        + "\nDisconnectFromDevice: Bad argument:";
                Utils.excMsg(ECGActivity.this, msg, ex);
                Log.d(TAG, this.getClass().getSimpleName()
                        + " resetDeviceId: " + msg);
            }
            mApi.shutDown();
            mApi = null;
        }
        mQRS = null;
        restart();
    }

    /**
     * Sets the QRS plot visibility from the current value of mUseQRSPlot.
     */
    private void setQRSVisibility() {
        Log.d(TAG, this.getClass().getSimpleName() + " setQRSVisibility:");
        if (mQRSPlot != null) {
            if (mUseQRSPlot) {
                mQRSPlot.setVisibility(View.VISIBLE);
            } else {
                mQRSPlot.setVisibility(View.GONE);
            }
        }
    }

    public void restart() {
        Log.d(TAG, this.getClass().getSimpleName() + " restart:"
                + " mApi=" + mApi
                + " mDeviceId=" + mDeviceId);
        if (mApi != null || mDeviceId == null || mDeviceId.isEmpty()) {
            return;
        }
        if (mEcgDisposable != null) {
            // Turns it off
            streamECG();
        }
        mPlaying = false;
        if (mECGPlotter != null) mECGPlotter.clear();
        if (mQRSPlotter != null) mQRSPlotter.clear();
        if (mHRPlotter != null) mHRPlotter.clear();
        mTextViewHR.setText("");
        mTextViewTime.setText("");
        mTextViewInfo.setText("");
        invalidateOptionsMenu();
        Toast.makeText(this,
                getString(R.string.connecting) + " " + mDeviceId,
                Toast.LENGTH_SHORT).show();

        // Don't use SDK if BT is not enabled or permissions are not granted.
        if (!mBleSupported) return;
        if (!isAllPermissionsGranted(this)) {
            if (!mAllPermissionsAsked) {
                mAllPermissionsAsked = true;
                Utils.warnMsg(this, getString(R.string.permission_not_granted));
            }
            return;
        }

        mApi = PolarBleApiDefaultImpl.defaultImplementation(this,
                PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING |
                        PolarBleApi.FEATURE_BATTERY_INFO |
                        PolarBleApi.FEATURE_DEVICE_INFO |
                        PolarBleApi.FEATURE_POLAR_FILE_TRANSFER |
                        PolarBleApi.FEATURE_HR);
        // DEBUG
        // Post a Runnable to have plots to be setup again in 1 sec
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
//            Log.d(TAG,
//                    "No connection handler: time=" + sdfShort.format(new
//                    Date()));
            if (!mConnected) {
                Utils.warnMsg(ECGActivity.this, "No connection to " + mDeviceId
                        + " after 1 minute");
            }
        }, 60000);
        mApi.setApiLogger(msg -> Log.d("PolarAPI", msg));
        mApi.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG, "BluetoothStateChanged " + b);
            }

            @Override
            public void deviceConnected(@NonNull PolarDeviceInfo s) {
                Log.d(TAG, "*Device connected " + s.deviceId);
                mAddress = s.address;
                mName = s.name;
                mConnected = true;
                // Set the MRU preference here after we know the name
                setDeviceMruPref(new DeviceInfo(mName, mDeviceId));
                Toast.makeText(ECGActivity.this,
                        getString(R.string.connected_string, s.name),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void deviceDisconnected(@NonNull PolarDeviceInfo s) {
                Log.d(TAG, "*Device disconnected " + s);
                mConnected = false;
            }

            @Override
            public void streamingFeaturesReady(@NonNull final String identifier,
                                               @NonNull final Set<PolarBleApi.DeviceStreamingFeature> features) {
                for (PolarBleApi.DeviceStreamingFeature feature : features) {
                    Log.d(TAG, "Streaming feature is ready for 1: " + feature);
                    switch (feature) {
                        case ECG:
                            streamECG();
                            break;
                        case PPI:
                        case ACC:
                        case MAGNETOMETER:
                        case GYRO:
                        case PPG:
                        default:
                            break;
                    }
                }
            }

            @Override
            public void hrFeatureReady(@NonNull String s) {
                Log.d(TAG, "*HR Feature ready " + s);
            }

            @Override
            public void disInformationReceived(@NonNull String s,
                                               @NonNull UUID u,
                                               @NonNull String s1) {
                if (u.equals(UUID.fromString("00002a28-0000-1000-8000" +
                        "-00805f9b34fb"))) {
                    mFirmware = s1.trim();
                    Log.d(TAG, "*Firmware: " + s + " " + mFirmware);
                    mTextViewInfo.setText(getString(R.string.info_string,
                            mName, mBatteryLevel, mFirmware, mDeviceId));
                }
            }

            @Override
            public void batteryLevelReceived(@NonNull String s, int i) {
                mBatteryLevel = Integer.toString(i);
                Log.d(TAG, "*Battery level " + s + " " + i);
                mTextViewInfo.setText(getString(R.string.info_string,
                        mName, mBatteryLevel, mFirmware, mDeviceId));
            }

            @Override
            public void hrNotificationReceived(@NonNull String s,
                                               @NonNull PolarHrData polarHrData) {
                if (mPlaying) {
//                    Log.d(TAG,
//                            "*HR " + polarHrData.hr + " mPlaying=" +
//                            mPlaying);
                    mTextViewHR.setText(String.valueOf(polarHrData.hr));
                    // Add to HR plot
                    long time = new Date().getTime();
                    mHRPlotter.addValues1(time, polarHrData.hr,
                            polarHrData.rrsMs);
                    mHRPlotter.fullUpdate();
                }
            }
        });
        try {
            mApi.connectToDevice(mDeviceId);
            mPlaying = true;
            setLastHr();
            mStopTime = new Date();
        } catch (PolarInvalidArgument ex) {
            String msg = "mDeviceId=" + mDeviceId
                    + "\nConnectToDevice: Bad argument:";
            Utils.excMsg(this, msg, ex);
            Log.d(TAG, "    restart: " + msg);
            mPlaying = false;
            setLastHr();
            mStopTime = new Date();
        }
        invalidateOptionsMenu();
//        Log.d(TAG, "    restart(end) mApi=" + mApi + " mPlaying=" + mPlaying);
    }

    /**
     * Class to hold a name and device ID.
     */
    public static class DeviceInfo {
        public String name;
        public String id;

        public DeviceInfo(String name, String id) {
            this.name = name;
            this.id = id;
        }
    }

    /**
     * Class to hold arrays for plotting.  The ecg array is the ECG values.
     * The peaks arrays is the same length and is true or false depending on if
     * the ECG value corrsponds to a peak. These are arrays as opposed to the
     * LinkedList's in the respective series.
     */
    public static class PlotArrays {
        public double[] ecg;
        public boolean[] peaks;

        public PlotArrays(double[] ecg, boolean[] peaks) {
            this.ecg = ecg;
            this.peaks = peaks;
        }
    }
}
