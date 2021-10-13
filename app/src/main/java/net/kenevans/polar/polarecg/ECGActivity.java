package net.kenevans.polar.polarecg;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.util.DisplayMetrics;
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
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.PanZoom;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
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
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;

public class ECGActivity extends AppCompatActivity implements IConstants,
        PlotterListener {
    SharedPreferences mSharedPreferences;
    private static final int MAX_DEVICES = 3;
    List<DeviceInfo> mMruDevices;
    // The defaults are for 130 Hz
    private int mSamplingRate = 130;
    private int mNLarge = 26;
    // The total number of points = nLarge * total large blocks desired
    private int mNTotalPoints = 150 * mNLarge;  // 150 = 30 sec
    private int mNPlotPoints = 20 * mNLarge;    // 20 points
    private XYPlot mPlot;
    private Plotter mPlotter;
    private PlotListener mPlotListener;
    private boolean mOrientationChanged = false;

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
        mPlot = findViewById(R.id.plot);
        mSharedPreferences = getPreferences(MODE_PRIVATE);
        mStopHR = mTextViewHR.getText().toString();
        mStopTime = new Date();


        // Start Bluetooth
        checkBT();
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

        if (mApi != null) mApi.foregroundEntered();
        invalidateOptionsMenu();

        // Setup the plot if not done
        Log.d(TAG, "    mPlotter=" + mPlotter);
        if (mPlotter == null) {
            mPlot.post(() -> {
                mPlotter =
                        new Plotter(mNTotalPoints, mNPlotPoints,
                                "ECG", Color.RED, false);
                mPlotter.setmListener(ECGActivity.this);
                setupPlot();
            });
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
                mPlotter.clear();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == REQ_GET_TREE && resultCode == RESULT_OK) {
            Uri treeUri;
            // Get Uri from Storage Access Framework.
            treeUri = intent.getData();
            // Keep them from accumulating
            UriUtils.releaseAllPermissions(this);
            SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE)
                    .edit();
            if (treeUri != null) {
                editor.putString(PREF_TREE_URI, treeUri.toString());
            } else {
                editor.putString(PREF_TREE_URI, null);
            }
            editor.apply();

            // Persist access permissions.
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            if (treeUri != null) {
                this.getContentResolver().takePersistableUriPermission(treeUri,
                        takeFlags);
            } else {
                Utils.errMsg(this, "Failed to get persistent access " +
                        "permissions");
            }
        }
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
        mPlot.post(() -> mPlot.redraw());
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

    /**
     * Sets the plot parameters, calculating the range boundaries to have the
     * same grid as the domain.  Calls update when done.
     */
    public void setupPlot() {
        Log.d(TAG, this.getClass().getSimpleName() + " setupPlot");
//        DisplayMetrics displayMetrics = this.getResources()
//        .getDisplayMetrics();
//        float dpHeight = displayMetrics.heightPixels / displayMetrics.density;
//        float dpWidth = displayMetrics.widthPixels / displayMetrics.density;
//        Log.d(TAG, "dpWidth=" + dpWidth + " dpHeight=" + dpHeight);
//        Log.d(TAG, "widthPixels=" + displayMetrics.widthPixels +
//                " heightPixels=" + displayMetrics.heightPixels);
//        Log.d(TAG, "density=" + displayMetrics.density);
//        Log.d(TAG, "10dp=" + 10 / displayMetrics.density + " pixels");
//
//        Log.d(TAG, "plotWidth=" + mPlot.getWidth() +
//                " plotHeight=" + mPlot.getHeight());
//
//        RectF widgetRect = mPlot.getGraph().getWidgetDimensions().canvasRect;
//        Log.d(TAG,
//                "widgetRect LRTB=" + widgetRect.left + "," + widgetRect
//                .right +
//                        "," + widgetRect.top + "," + widgetRect.bottom);
//        Log.d(TAG, "widgetRect width=" + (widgetRect.right - widgetRect
//        .left) +
//                " height=" + (widgetRect.bottom - widgetRect.top));
//
//        RectF gridRect = mPlot.getGraph().getGridRect();
//        Log.d(TAG, "gridRect LRTB=" + gridRect.left + "," + gridRect.right +
//                "," + gridRect.top + "," + gridRect.bottom);
//        Log.d(TAG, "gridRect width=" + (gridRect.right - gridRect.left) +
//                " height=" + (gridRect.bottom - gridRect.top));

        // Calculate the range limits to make the blocks be square
        // Using .5 mV and nLarge / samplingRate for total grid size
        // rMax is half the total, rMax at top and -rMax at bottom
        RectF gridRect = mPlot.getGraph().getGridRect();
        double rMax =
                .25 * (gridRect.bottom - gridRect.top) * mNPlotPoints /
                        mNLarge / (gridRect.right - gridRect.left);
        // Round it to one decimal point
        rMax = Math.round(rMax * 10) / 10.;
        Log.d(TAG, "    rMax = " + rMax);
        Log.d(TAG, "    gridRect LRTB=" + gridRect.left + "," + gridRect.right +
                "," + gridRect.top + "," + gridRect.bottom);
        Log.d(TAG, "    gridRect width=" + (gridRect.right - gridRect.left) +
                " height=" + (gridRect.bottom - gridRect.top));
        DisplayMetrics displayMetrics = this.getResources()
                .getDisplayMetrics();
        Log.d(TAG, "    display widthPixels=" + displayMetrics.widthPixels +
                " heightPixels=" + displayMetrics.heightPixels);

        mPlot.addSeries(mPlotter.getmSeries(), mPlotter.getmFormatter());
        mPlot.setRangeBoundaries(-rMax, rMax, BoundaryMode.FIXED);
        // Set the range block to be .1 mV so a large block will be .5 mV
        mPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, .1);
        mPlot.setLinesPerRangeLabel(5);
        mPlot.setDomainBoundaries(0, mNPlotPoints, BoundaryMode.FIXED);
        // Set the domain block to be .2 * nlarge so large block will be
        // nLarge samples
        mPlot.setDomainStep(StepMode.INCREMENT_BY_VAL,
                .2 * mNLarge);
        mPlot.setLinesPerDomainLabel(5);

        mPlot.getGraph().setLineLabelEdges(XYGraphWidget.Edge.NONE);

        // These don't work
//        mPlot.getTitle().position(0, HorizontalPositioning
//        .ABSOLUTE_FROM_RIGHT,
//                0,    VerticalPositioning.ABSOLUTE_FROM_TOP, Anchor
//                .RIGHT_TOP);
//        mPlot.getTitle().setAnchor(Anchor.BOTTOM_MIDDLE);
//        mPlot.getTitle().setMarginTop(200);
//        mPlot.getTitle().setPaddingTop(200);

//        mPlot.setRenderMode(Plot.RenderMode.USE_BACKGROUND_THREAD);

        update();
    }

    public void resetPlot(int samplingRate) {
        mSamplingRate = samplingRate;
        mNLarge = (int) Math.round(.2 * samplingRate);
        mNTotalPoints = 150 * mNLarge;
        mNPlotPoints = 20 * mNLarge;
        mPlot.post(() -> {
            mPlotter =
                    new Plotter(mNTotalPoints, mNPlotPoints,
                            "PPG", Color.RED, false);
            mPlotter.setmListener(ECGActivity.this);
            setupPlot();
        });
    }

    private void allowPan(boolean allow) {
        if (allow) {
            PanZoom.attach(mPlot, PanZoom.Pan.HORIZONTAL,
                    PanZoom.Zoom.NONE);
        } else {
            PanZoom.attach(mPlot, PanZoom.Pan.NONE, PanZoom.Zoom.NONE);
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
                        mPlotter.getmSeries().getyVals();
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
                LinkedList<Number> vals = mPlotter.getmSeries().getyVals();
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
        if (mPlotter != null && mPlotter.getmSeries() !=
                null && mPlotter.getmSeries().getyVals() != null) {
            double elapsed =
                    mPlotter.getmDataIndex() / (double) mSamplingRate;
            msg.append("Elapsed Time: ")
                    .append(getString(R.string.elapsed_time, elapsed)).append("\n");
            msg.append("Points plotted: ")
                    .append(mPlotter.getmSeries().getyVals().size()).append(
                    "\n");
        }
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
        if (mEcgDisposable == null) {
            mEcgDisposable =
                    mApi.requestStreamSettings(mDeviceId,
                            PolarBleApi.DeviceStreamingFeature.ECG).
                            toFlowable().
                            flatMap((Function<PolarSensorSetting,
                                    Publisher<PolarEcgData>>) sensorSetting -> {
    //                            Log.d(TAG, "mEcgDisposable requestEcgSettings " +
    //                                    "apply");
    //                            Log.d(TAG,
    //                                    "sampleRate=" + sensorSetting
    //                                    .maxSettings().settings.
    //                                            get(PolarSensorSetting
    //                                            .SettingType.SAMPLE_RATE) +
    //                                            " resolution=" + sensorSetting
    //                                            .maxSettings().settings.
    //                                            get(PolarSensorSetting
    //                                            .SettingType.RESOLUTION) +
    //                                            " range=" + sensorSetting
    //                                            .maxSettings().settings.
    //                                            get(PolarSensorSetting
    //                                            .SettingType.RANGE));
                                        return mApi.startEcgStreaming(mDeviceId,
                                                sensorSetting.maxSettings());
                                    }).observeOn(AndroidSchedulers.mainThread()).subscribe(
                            polarEcgData -> {
//                                    double deltaT =
//                                            .000000001 * (polarEcgData
//                                            .timeStamp - ecgTime0);
//                                    ecgTime0 = polarEcgData.timeStamp;
//                                    int nSamples = polarEcgData.samples
//                                    .size();
//                                    double samplesPerSec = nSamples / deltaT;
//                                    Log.d(TAG,
//                                            "ecg update:" +
//                                                    " deltaT=" + String
//                                                    .format("%.3f", deltaT) +
//                                                    " nSamples=" + nSamples +
//                                                    " samplesPerSec=" +
//                                                    String.format("%.3f",
//                                                    samplesPerSec));

//                                    long now = new Date().getTime();
//                                    long ts =
//                                            polarEcgData.timeStamp / 1000000;
//                                    Log.d(TAG, "timeOffset=" + (now - ts) +
//                                            " " + new Date(now - ts));
                                if (mPlaying) {
                                    mPlotter.addValues(mPlot, polarEcgData);
                                    double elapsed =
                                            mPlotter.getmDataIndex() / 130.;
                                    mTextViewTime.setText(getString(R.string.elapsed_time, elapsed));
                                }
                            },
                            throwable -> {
                                Log.e(TAG,
                                        "ECG Error: " + throwable.getLocalizedMessage());
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

    @Override
    public void update() {
        runOnUiThread(() -> {
//                Log.d(TAG, "update (UI) thread: " + Thread.currentThread()
//                .getName());
            mPlot.redraw();
        });
    }

    public void checkBT() {
        BluetoothAdapter mBluetoothAdapter =
                BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent =
                    new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 2);
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
        startActivityForResult(intent, REQ_GET_TREE);
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
        if (mPlotter != null) mPlotter.clear();
        mTextViewHR.setText("");
        mTextViewTime.setText("");
        mTextViewFW.setText("");
        invalidateOptionsMenu();
        Toast.makeText(this,
                getString(R.string.connecting) + " " + mDeviceId,
                Toast.LENGTH_SHORT).show();
        if (mPlotListener != null) {
            mPlot.removeListener(mPlotListener);
            mPlotListener = null;
        }
        mPlotListener = new PlotListener() {
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
                    setupPlot();
                    mPlotter.updatePlot(mPlot);
                }
            }
        };
        mPlot.addListener(mPlotListener);

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
                Toast.makeText(ECGActivity.this, R.string.connected,
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
