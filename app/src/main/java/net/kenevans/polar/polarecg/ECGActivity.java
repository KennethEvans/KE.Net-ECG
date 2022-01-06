package net.kenevans.polar.polarecg;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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

import com.androidplot.Plot;
import com.androidplot.PlotListener;
import com.androidplot.xy.PanZoom;
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
import java.io.IOException;
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
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Function;

public class ECGActivity extends AppCompatActivity implements IConstants {
    SharedPreferences mSharedPreferences;
    private static final int MAX_DEVICES = 3;
    // Currently the sampling rate is fixed at 130
    private static final int mSamplingRate = 130;
    List<DeviceInfo> mMruDevices;
    private XYPlot mECGPlot;
    private XYPlot mHRPlot;
    private XYPlot mQRSPlot;
    private ECGPlotter mECGPlotter;
    private HRPlotter mHRPlotter;
    private QRSPlotter mQRSPlotter;
    private PlotListener mEcgPlotListener;
    private boolean mOrientationChanged = false;
    private QRSDetection qrs;

    //    public boolean useQRSPlotter = true;
    public boolean mUseQRSPlot = true;

    /***
     * Whether to save as CSV, Plot, or both.
     */
    private enum SAVE_TYPE {DATA, PLOT, BOTH}

    TextView mTextViewHR, mTextViewFW, mTextViewTime;
    private PolarBleApi mApi;
    private Disposable mEcgDisposable;
    private boolean mPlaying;
    private Menu mMenu;
    private String mDeviceId = "Unknown";
    private String mFirmware = "Unknown";
    private String mName = "Unknown";
    private String mAddress = "Unknown";
    private String mBatteryLevel = "Unknown";
    //* HR when stopped playing. Updated whenever playing started or stopped. */
    private String mStopHR;
    //* Date when stopped playing. Updated whenever playing started or
    // stopped. */
    private Date mStopTime;
    private Date mStartTime;

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
                        if (result.getResultCode() == RESULT_OK) {
                            // Get Uri from Storage Access Framework.
                            Uri treeUri = result.getData().getData();
                            SharedPreferences.Editor editor =
                                    getPreferences(MODE_PRIVATE)
                                            .edit();
                            if (treeUri == null) {
                                editor.putString(PREF_TREE_URI, null);
                                editor.apply();
                                Utils.errMsg(this, "Failed to get persistent " +
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
                    });

//    // Used in Logging
//    private long ecgTime0;
//    private long redrawTime0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, this.getClass().getSimpleName() + " onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ecg);
        mTextViewHR = findViewById(R.id.info);
        mTextViewFW = findViewById(R.id.fw);
        mTextViewTime = findViewById(R.id.time);
        mECGPlot = findViewById(R.id.ecgplot);
        mHRPlot = findViewById(R.id.hrplot);
        mQRSPlot = findViewById(R.id.qrsplot);

        // Make the QRS plot and it's layout Gone
        if (!mUseQRSPlot) {
            mQRSPlot.setVisibility(View.GONE);
            ConstraintLayout constraintLayout = findViewById(R.id.qrs);
            constraintLayout.setVisibility(View.GONE);
//            // Reset the weights
//            constraintLayout = findViewById(R.id.top);
//            ConstraintSet constraintSet = new ConstraintSet();
//            constraintSet.clone(constraintLayout);
//            constraintSet.setVerticalWeight(R.id.ecg, 0.7f);
//            constraintSet.setVerticalWeight(R.id.hr, 0.3f);
        }

        mSharedPreferences = getPreferences(MODE_PRIVATE);
        mStopHR = mTextViewHR.getText().toString();
        mStopTime = new Date();

        // Start Bluetooth
        mDeviceId = mSharedPreferences.getString(PREF_DEVICE_ID, "");
        Log.d(TAG, "    mDeviceId=" + mDeviceId);
        checkBT();
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
    }

    @Override
    protected void onPause() {
        Log.v(TAG, this.getClass().getSimpleName() + " onPause");
        super.onPause();

        if (mApi != null) mApi.backgroundEntered();
    }

    @Override
    public void onResume() {
        Log.v(TAG, this.getClass().getSimpleName() + " onResume:"
                + " mOrientationChanged=" + mOrientationChanged);
        super.onResume();

        // Check if PREF_TREE_URI is valid and remove it id not
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
        Log.d(TAG,
                "    mECGPlotter=" + mECGPlotter + " mHRPlotter=" + mHRPlotter
                        + " mQRSPlotter=" + mQRSPlotter);
        if (mECGPlotter == null) {
            mECGPlot.post(() -> mECGPlotter =
                    new ECGPlotter(this, mECGPlot,
                            "ECG", Color.RED, false));
        }
        if (mHRPlotter == null) {
            mHRPlot.post(() -> mHRPlotter =
                    new HRPlotter(this, mHRPlot,
                            "HR-RR", false));
        }
        if (mQRSPlotter == null) {
            mQRSPlot.post(() -> mQRSPlotter =
                    new QRSPlotter(this, mQRSPlot, "HR-RR"));
        }

        // Start the connection to the device
        Log.d(TAG, "    mDeviceId=" + mDeviceId);
        Log.d(TAG, "    mApi=" + mApi);
        mDeviceId = mSharedPreferences.getString(PREF_DEVICE_ID, "");
        if (mDeviceId == null || mDeviceId.isEmpty()) {
            Toast.makeText(this,
                    getString(R.string.noDevice),
                    Toast.LENGTH_SHORT).show();
            return;
        } else {
            restart();
        }
        Log.d(TAG, "    onResume(end) mPlaying=" + mPlaying);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.v(TAG, this.getClass().getSimpleName() + " onCreateOptionsMenu");
        Log.d(TAG, "    mPlaying=" + mPlaying);
        mMenu = menu;
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        if (mApi == null) {
            mMenu.findItem(R.id.pause).setTitle("Start");
            mMenu.findItem(R.id.save_data).setVisible(false);
            mMenu.findItem(R.id.save_plot).setVisible(false);
            mMenu.findItem(R.id.save_both).setVisible(false);
            mMenu.findItem(R.id.save_both).setVisible(true);
            mMenu.findItem(R.id.device_id).setVisible(true);
        } else if (mPlaying) {
            mMenu.findItem(R.id.pause).setIcon(ResourcesCompat.
                    getDrawable(getResources(),
                            R.drawable.ic_stop_white_36dp, null));
            mMenu.findItem(R.id.pause).setTitle("Pause");
            mMenu.findItem(R.id.save_data).setVisible(false);
            mMenu.findItem(R.id.save_plot).setVisible(false);
            mMenu.findItem(R.id.save_both).setVisible(false);
        } else {
            mMenu.findItem(R.id.pause).setIcon(ResourcesCompat.
                    getDrawable(getResources(),
                            R.drawable.ic_play_arrow_white_36dp, null));
            mMenu.findItem(R.id.pause).setTitle("Start");
            mMenu.findItem(R.id.save_data).setVisible(true);
            mMenu.findItem(R.id.save_plot).setVisible(true);
            mMenu.findItem(R.id.save_both).setVisible(true);
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
                mStopHR = mTextViewHR.getText().toString();
                mStopTime = new Date();
                mPlaying = false;
                allowPan(true);
                if (mEcgDisposable != null) {
                    // Turns it off
                    streamECG();
                }
                mMenu.findItem(R.id.pause).setIcon(ResourcesCompat.
                        getDrawable(getResources(),
                                R.drawable.ic_play_arrow_white_36dp, null));
                mMenu.findItem(R.id.pause).setTitle("Start");
                mMenu.findItem(R.id.save_data).setVisible(true);
                mMenu.findItem(R.id.save_plot).setVisible(true);
                mMenu.findItem(R.id.save_both).setVisible(true);
            } else {
                // Turn it on
                mStopHR = mTextViewHR.getText().toString();
                mStopTime = new Date();
                mPlaying = true;
                allowPan(false);
                mTextViewTime.setText(getString(R.string.elapsed_time,
                        0.0));
                // Clear the plot
                mECGPlotter.clear();
                mQRSPlotter.clear();
                if (mEcgDisposable == null) {
                    // Turns it on
                    streamECG();
                }
                mMenu.findItem(R.id.pause).setIcon(ResourcesCompat.
                        getDrawable(getResources(),
                                R.drawable.ic_stop_white_36dp, null));
                mMenu.findItem(R.id.pause).setTitle("Pause");
                mMenu.findItem(R.id.save_data).setVisible(false);
                mMenu.findItem(R.id.save_plot).setVisible(false);
                mMenu.findItem(R.id.save_both).setVisible(false);
            }
            return true;
        } else if (id == R.id.save_plot) {
            saveData(SAVE_TYPE.PLOT);
            return true;
        } else if (id == R.id.save_data) {
            saveData(SAVE_TYPE.DATA);
            return true;
        } else if (id == R.id.save_both) {
            saveData(SAVE_TYPE.BOTH);
            return true;
        } else if (id == R.id.info) {
            info();
            return true;
        } else if (id == R.id.device_id) {
            selectDeviceId();
            return true;
        } else if (id == R.id.choose_data_directory) {
            chooseDataDirectory();
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
     * The screen orientation changes have not been made yet, so anything
     * relying on them must be done later.
     *
     * @param newConfig The new configuration.
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Log.v(TAG, this.getClass().getSimpleName() +
                " onConfigurationChanged");
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.v(TAG, "    Landscape");
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.v(TAG, "    Portrait");
        }
        // Cannot do this now as the screen changes have only been dispatched
        mOrientationChanged = true;
        mECGPlot.post(() -> mECGPlot.redraw());
    }


    @Override
    public void onDestroy() {
        Log.v(TAG, this.getClass().getSimpleName() + " onDestroy");
        super.onDestroy();
        if (mApi != null) mApi.shutDown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[]
            permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions,
                grantResults);
        Log.d(TAG, this.getClass().getSimpleName() + ": " +
                "onRequestPermissionsResult:" + " permissions=" +
                permissions[0]);
        Log.d(TAG, "    grantResults=" + grantResults[0]);
        Log.d(TAG, "    requestCode=" + requestCode
                + " ACCESS_LOCATION_REQ="
                + REQ_ACCESS_LOCATION
        );
//        switch (requestCode) {
//        }
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
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < mMruDevices.size()) {
                            DeviceInfo deviceInfo = mMruDevices.get(which);
                            String oldDeviceId = mDeviceId;
                            setDeviceMruPref(deviceInfo);
                            Log.d(TAG, "which=" + which
                                    + " name=" + deviceInfo.name + " id=" + deviceInfo.id);
                            Log.d(TAG,
                                    "selectDeviceId: oldDeviceId=" + oldDeviceId
                                            + " mDeviceId=" + mDeviceId);
                            if (!oldDeviceId.equals(mDeviceId)) {
                                if (mApi != null) {
                                    try {
                                        mApi.disconnectFromDevice(oldDeviceId);
                                    } catch (PolarInvalidArgument ex) {
                                        String msg = "disconnectFromDevice: " +
                                                "Bad " +
                                                "argument: mDeviceId"
                                                + mDeviceId;
                                        Utils.excMsg(ECGActivity.this, msg, ex);
                                        Log.d(TAG,
                                                this.getClass().getSimpleName()
                                                        + " showDeviceIdDialog: " + msg);
                                    }
                                    mApi = null;
                                }
                                restart();
                            }
                        } else {
                            showDeviceIdDialog(null);
                        }
                        dialog.dismiss();
                    }
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
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
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
                            if (mApi != null) {
                                try {
                                    mApi.disconnectFromDevice(oldDeviceId);
                                } catch (PolarInvalidArgument ex) {
                                    String msg = "disconnectFromDevice: Bad " +
                                            "argument: mDeviceId"
                                            + mDeviceId;
                                    Utils.excMsg(ECGActivity.this, msg, ex);
                                    Log.d(TAG, this.getClass().getSimpleName()
                                            + " showDeviceIdDialog: " + msg);
                                }
                                mApi = null;
                            }
                            restart();
                        }
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

//    public void resetPlot(int samplingRate) {
//        mSamplingRate = samplingRate;
//        mNLarge = (int) Math.round(.2 * samplingRate);
//        mNTotalPoints = 150 * mNLarge;
//        mNPlotPoints = 20 * mNLarge;
//        mECGPlot.post(() -> {
//            mECGPlotter =
//                    new ECGPlotter(this, mECGPlot,
//                            "PPG", Color.RED, false);
//        });
//    }

    private void allowPan(boolean allow) {
        if (allow) {
            PanZoom.attach(mECGPlot, PanZoom.Pan.HORIZONTAL,
                    PanZoom.Zoom.NONE);
        } else {
            PanZoom.attach(mECGPlot, PanZoom.Pan.NONE, PanZoom.Zoom.NONE);
        }
        if (mUseQRSPlot) {
            if (allow) {
                PanZoom.attach(mQRSPlot, PanZoom.Pan.HORIZONTAL,
                        PanZoom.Zoom.NONE);
            } else {
                PanZoom.attach(mQRSPlot, PanZoom.Pan.NONE, PanZoom.Zoom.NONE);
            }

        }

    }

    /**
     * Save the current samples as a file.  Prompts for a note, then calls
     * finishSaveData.
     *
     * @param saveType The SAVE_TYPE.
     */
    private void saveData(final SAVE_TYPE saveType) {
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
                final LinkedList<Number> vals =
                        mECGPlotter.getSeries().getyVals();
                final int nSamples = vals.size();
                Bitmap bm = EcgImage.createImage(this,
                        mSamplingRate,
                        mStopTime.toString(),
                        mDeviceId,
                        mFirmware,
                        mBatteryLevel,
                        note,
                        mStopHR,
                        String.format(Locale.US, "%.1f " +
                                "sec", nSamples / (double) mSamplingRate),
                        vals);
                bm.compress(Bitmap.CompressFormat.PNG, 80, strm);
                strm.close();
                msg = "Wrote " + docUri.getLastPathSegment();
                Log.d(TAG, msg);
                Utils.infoMsg(this, msg);
            }
        } catch (IOException ex) {
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
                out.write(mStopTime.toString() + "\n");
                // The text for this TextView already has a \n
                out.write(mTextViewFW.getText().toString());
                out.write(note + "\n");
                out.write("HR " + mStopHR + "\n");
                // Write samples
                LinkedList<Number> vals = mECGPlotter.getSeries().getyVals();
                int nSamples = vals.size();
                out.write(nSamples + " values " + String.format(Locale.US
                        , "%" +
                                ".1f " +
                                "sec\n", nSamples / (double) mSamplingRate));
                for (Number val : vals) {
                    out.write(String.format(Locale.US, "%.3f\n",
                            val.doubleValue()));
                }
                out.flush();
                msg = "Wrote " + docUri.getLastPathSegment();
                Log.d(TAG, msg);
                Utils.infoMsg(this, msg);
            }
        } catch (IOException ex) {
            msg = "Error writing CSV file";
            Log.e(TAG, msg);
            Log.e(TAG, Log.getStackTraceString(ex));
            Utils.excMsg(this, msg, ex);
        }
    }

    public void info() {
        StringBuilder msg = new StringBuilder();
        msg.append("Name: ").append(mName).append("\n");
        msg.append("Device Id: ").append(mDeviceId).append("\n");
        msg.append("Address: ").append(mAddress).append("\n");
        msg.append("Firmware: ").append(mFirmware).append("\n");
        msg.append("Battery Level: ").append(mBatteryLevel).append("\n");
        msg.append("Connected: ").append((mApi != null)).append("\n");
        msg.append("Playing: ").append(mPlaying).append("\n");
        msg.append("Receiving ECG: ").append(mEcgDisposable != null).append(
                "\n");
        if (mECGPlotter != null && mECGPlotter.getSeries() !=
                null && mECGPlotter.getSeries().getyVals() != null) {
            double elapsed =
                    mECGPlotter.getDataIndex() / (double) mSamplingRate;
            msg.append("Elapsed Time: ")
                    .append(getString(R.string.elapsed_time, elapsed)).append("\n");
            msg.append("Points plotted: ")
                    .append(mECGPlotter.getSeries().getyVals().size()).append(
                    "\n");
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
        msg.append("Location Permission: ")
                .append((ContextCompat.checkSelfPermission(this, Manifest
                        .permission.ACCESS_FINE_LOCATION) == PackageManager
                        .PERMISSION_GRANTED)).append("\n");
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
        Log.v(TAG, this.getClass().getSimpleName() + " streamECG:"
                + "mEcgDisposable=" + mEcgDisposable);
        SimpleDateFormat sdf =
                new SimpleDateFormat("dd:MM:yyyy HH:mm:ss", Locale.US);
        SimpleDateFormat sdf2 =
                new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US);
        /// Use UTC
        String tz = " UTC";
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        sdf2.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Date epoch = sdf.parse("01:01:2000 00:00:00");
            Date epoch1 = sdf.parse("01:01:2019 00:00:00");
//            Log.d(TAG, "epoch=" + epoch + " " + epoch.getTime()
//                    + " epoch1=" + epoch1 + " " + epoch1.getTime());
            Log.d(TAG, "epoch=" + sdf2.format(epoch)
                    + " epoch1=" + sdf2.format(epoch1)
                    + " " + epoch.getTime()
                    + " " + epoch1.getTime()
                    + " " + (epoch1.getTime() - epoch.getTime())
                    + tz);
        } catch (
                Exception ex) {
            Log.e(TAG, "Error parsing date", ex);
        }
        if (mEcgDisposable == null) {
            // Set the local time to get correct timestamps. H10 apparently
            // resets its time to 01:01:2019 00:00:00 when connected to strap
            Calendar calNow = Calendar.getInstance();
//            mApi.setLocalTime(mDeviceId, calNow);
            Log.d(TAG, "Doing setLocalTime to " + calNow.getTime());
            mEcgDisposable =
//                    mApi.setLocalTime(mDeviceId, calNow)
//                    .andThen(
//                            mApi.requestStreamSettings(mDeviceId,
//                            PolarBleApi.DeviceStreamingFeature.ECG))
                    mApi.requestStreamSettings(mDeviceId,
                            PolarBleApi.DeviceStreamingFeature.ECG)
                    .toFlowable()
                    .flatMap((Function<PolarSensorSetting,
                            Publisher<PolarEcgData>>) sensorSetting -> {
                        //                            Log.d(TAG,
                        //                            "mEcgDisposable
                        //                            requestEcgSettings " +
                        //                                    "apply");
                        //                            Log.d(TAG,
                        //                                    "sampleRate=" +
                        //                                    sensorSetting
                        //                                    .maxSettings()
                        //                                    .settings.
                        //                                            get
                        //                                            (PolarSensorSetting
                        //                                            .SettingType.SAMPLE_RATE) +
                        //                                            "
                        //                                            resolution=" + sensorSetting
                        //                                            .maxSettings().settings.
                        //                                            get
                        //                                            (PolarSensorSetting
                        //                                            .SettingType.RESOLUTION) +
                        //                                            "
                        //                                            range="
                        //                                            +
                        //                                            sensorSetting
                        //                                            .maxSettings().settings.
                        //                                            get
                        //                                            (PolarSensorSetting
                        //                                            .SettingType.RANGE));
                        return mApi.startEcgStreaming(mDeviceId,
                                sensorSetting.maxSettings());
                    }).observeOn(AndroidSchedulers.mainThread())
                    .subscribe(polarEcgData -> {
//                                long offset0 = 946684800000L; // epoch to Polar
//                                long offset1 = 599616000000L; // Polar to
//                                // default
//                                long ts = polarEcgData.timeStamp / 1000000;
//                                long ts1 = ts + offset0;
//                                long ts2 = ts + offset0 + offset1;
//                                long now = new Date().getTime();
//                                long reset = now - ts1;
//                                Log.d(TAG, String.format("%15d %-8s %.2f " +
//                                        "years", offset1, "offset1", msToYears
//                                        (offset1)));
//                                Log.d(TAG, String.format("%15d %-8s %.2f " +
//                                        "years", ts, "ts", msToYears(ts)));
//                                Log.d(TAG, String.format("%15d %-8s %.2f ",
//                                        ts1, "ts1", msToYears(ts1)));
//                                Log.d(TAG, String.format("%15d %-8s %.2f ",
//                                        ts2, "ts2", msToYears(ts2)));
//                                Log.d(TAG, String.format("%15d %-8s %.2f ",
//                                        now, "now", msToYears(now)));
////                                Log.d(TAG, "ts=" + ts + " ts1=" + ts1
////                                        + " ts2=" + ts2  + " now=" + now);
//                                Log.d(TAG,
//                                        "ts=" + new Date(ts) + " ts1="
//                                                + " " + new Date(ts1)
//                                                + " reset="
//                                                + " " + new Date(reset));
                                if (qrs == null) {
                                    qrs = new QRSDetection(ECGActivity.this,
                                            mECGPlotter, mHRPlotter,
                                            mQRSPlotter);
                                }
//                                Log.d(TAG, this.getClass().getSimpleName() + " streamECG"
//                                        + " thread=" + Thread.currentThread().getName());
//                                Log.d(TAG, this.getClass().getSimpleName() + " streamECG"
//                                        + " nSamples=" + polarEcgData.samples.size());
                                qrs.process(polarEcgData);
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
                                Utils.excMsg(ECGActivity.this, "ECG Error: ",
                                        throwable);
                                mEcgDisposable = null;
                            },
                            () -> Log.d(TAG, "ECG streaming complete")
                    );
        } else {
            // NOTE stops streaming if it is "running"
            mEcgDisposable.dispose();
            mEcgDisposable = null;
        }
    }

    public double msToDays(long longVal) {
        return longVal / (1000. * 60. * 60. * 24.);
    }

    public double msToYears(long longVal) {
        return longVal / (1000. * 60. * 60. * 24. * 365.);
    }

    public void checkBT() {
        Log.d(TAG, "checkBT");
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

        //requestPermissions() method needs to be called when the build SDK
        // version is 23 or above
        if (Build.VERSION.SDK_INT >= 23) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_ACCESS_LOCATION);
        }
    }

    /**
     * Brings up a system file chooser to get the data directory
     */
    private void chooseDataDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION &
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
//        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uriToLoad);
//        // Deprecated
//        startActivityForResult(intent, REQ_GET_TREE);
        openDocumentTreeLauncher.launch(intent);
    }

    public void restart() {
        Log.v(TAG, this.getClass().getSimpleName() + " restart:"
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
        mTextViewHR.setText("");
        mTextViewTime.setText("");
        mTextViewFW.setText("");
        invalidateOptionsMenu();
        Toast.makeText(this,
                getString(R.string.connecting) + " " + mDeviceId,
                Toast.LENGTH_SHORT).show();
        if (mEcgPlotListener != null) {
            mECGPlot.removeListener(mEcgPlotListener);
            mEcgPlotListener = null;
        }
        mEcgPlotListener = new PlotListener() {
            @Override
            public void onBeforeDraw(Plot source, Canvas canvas) {
            }

            @Override
            public void onAfterDraw(Plot source, Canvas canvas) {
//                long now = new Date().getTime();
//                Log.d(TAG,
//                        "onAfterDraw: mOrientationChanged=" +
//                        mOrientationChanged +
//                                " deltaT=" + (now - redrawTime0));
//                redrawTime0 = now;
                if (mOrientationChanged) {
                    mOrientationChanged = false;
                    Log.d(TAG, "onAfterDraw: orientation changed");
                    mECGPlotter.setupPlot();
                    mECGPlotter.updateDomainBoundaries();
                    mECGPlotter.update();
                }
            }
        };
        mECGPlot.addListener(mEcgPlotListener);

        mApi = PolarBleApiDefaultImpl.defaultImplementation(this,
                PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING |
                        PolarBleApi.FEATURE_BATTERY_INFO |
                        PolarBleApi.FEATURE_DEVICE_INFO |
                        PolarBleApi.FEATURE_HR);
        mApi.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG, "*BluetoothStateChanged " + b);
            }

            @Override
            public void deviceConnected(@NonNull PolarDeviceInfo s) {
                Log.d(TAG, "*Device connected " + s.deviceId);
                mAddress = s.address;
                mName = s.name;
                // Set the MRU preference here after we know the name
                setDeviceMruPref(new DeviceInfo(mName, mDeviceId));
                Toast.makeText(ECGActivity.this,
                        getString(R.string.connected_string, s.name),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void deviceDisconnected(@NonNull PolarDeviceInfo s) {
                Log.d(TAG, "*Device disconnected " + s);
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
                    mTextViewFW.setText(getString(R.string.info_string,
                            mName, mBatteryLevel, mFirmware, mDeviceId));
                }
            }

            @Override
            public void batteryLevelReceived(@NonNull String s, int i) {
                mBatteryLevel = Integer.toString(i);
                Log.d(TAG, "*Battery level " + s + " " + i);
                mTextViewFW.setText(getString(R.string.info_string,
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
                    mHRPlotter.addHrValue(time, polarHrData.hr);
                    int nRr = polarHrData.rrsMs.size();
                    if (nRr > 0) {
                        mHRPlotter.addRrValues(time, polarHrData.rrsMs);
                    }
                    mHRPlotter.update();
                }
            }
        });
        try {
            mApi.connectToDevice(mDeviceId);
            mPlaying = true;
            mStopHR = mTextViewHR.getText().toString();
            mStopTime = new Date();
        } catch (PolarInvalidArgument ex) {
            String msg = "connectToDevice: Bad argument: mDeviceId" + mDeviceId;
            Utils.excMsg(this, msg, ex);
            Log.d(TAG, "    restart: " + msg);
            mPlaying = false;
            mStopHR = mTextViewHR.getText().toString();
            mStopTime = new Date();
        }
        invalidateOptionsMenu();
        Log.d(TAG, "    restart(end) mApi=" + mApi + " mPlaying=" + mPlaying);
    }

    public class DeviceInfo {
        public String name;
        public String id;

        public DeviceInfo(String name, String id) {
            this.name = name;
            this.id = id;
        }
    }
}
