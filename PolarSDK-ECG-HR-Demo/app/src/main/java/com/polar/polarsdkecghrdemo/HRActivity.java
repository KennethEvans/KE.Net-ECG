package com.polar.polarsdkecghrdemo;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;

import io.reactivex.disposables.Disposable;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarHrData;

public class HRActivity extends AppCompatActivity implements PlotterListener {
    private static final int DURATION = 300000;  // In ms
    private XYPlot plot;
    private TimePlotter plotter;

    TextView textViewHR, textViewFW;
    private String TAG = "Polar_HRActivity";
    public PolarBleApi api;
    private Disposable ecgDisposable = null;
    private Context classContext = this;
    private String DEVICE_ID;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hr);
        DEVICE_ID = getIntent().getStringExtra("id");
        textViewHR = findViewById(R.id.info2);
        textViewFW = findViewById(R.id.fw2);

        plot = findViewById(R.id.plot2);

        api = PolarBleApiDefaultImpl.defaultImplementation(this,
                PolarBleApi.FEATURE_BATTERY_INFO |
                        PolarBleApi.FEATURE_DEVICE_INFO |
                        PolarBleApi.FEATURE_HR);
        api.setPolarFilter(false);
        api.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG, "BluetoothStateChanged " + b);
            }

            @Override
            public void polarDeviceConnected(PolarDeviceInfo s) {
                Log.d(TAG, "Device connected " + s.deviceId);
                String msg = s.name + "\n" + s.deviceId;
                textViewFW.append(msg + "\n");

                Toast.makeText(classContext, R.string.connected,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void polarDeviceConnecting(PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "CONNECTING: " + polarDeviceInfo.deviceId);
            }

            @Override
            public void polarDeviceDisconnected(PolarDeviceInfo s) {
                Log.d(TAG, "Device disconnected " + s);

            }

            @Override
            public void ecgFeatureReady(String s) {
                Log.d(TAG, "ECG Feature ready " + s);
            }

            @Override
            public void accelerometerFeatureReady(String s) {
                Log.d(TAG, "ACC Feature ready " + s);
            }

            @Override
            public void ppgFeatureReady(String s) {
                Log.d(TAG, "PPG Feature ready " + s);
            }

            @Override
            public void ppiFeatureReady(String s) {
                Log.d(TAG, "PPI Feature ready " + s);
            }

            @Override
            public void biozFeatureReady(String s) {

            }

            @Override
            public void hrFeatureReady(String s) {
                Log.d(TAG, "HR Feature ready " + s);
            }

            @Override
            public void fwInformationReceived(String s, String fw) {
                Log.d(TAG, "Firmware: " + s + " " + fw.trim());
                // Don't write if the information is empty
                if (!fw.isEmpty()) {
                    String msg = "Firmware: " + fw.trim();
                    textViewFW.append(msg + "\n");
                }
            }

            @Override
            public void batteryLevelReceived(String s, int i) {
                Log.d(TAG, "Battery level " + s + " " + i);
                String msg = "Battery level: " + i;
//                Toast.makeText(classContext, msg, Toast.LENGTH_LONG).show();
                textViewFW.append(msg + "\n");
            }

            @Override
            public void hrNotificationReceived(String s,
                                               PolarHrData polarHrData) {
                Log.d(TAG, "HR " + polarHrData.hr);
                List<Integer> rrsMs = polarHrData.rrsMs;
                StringBuilder msg = new StringBuilder();
                msg.append(polarHrData.hr).append("\n");
                for (int i : rrsMs) {
                    msg.append(i).append(",");
                }
                if (msg.toString().endsWith(",")) {
                    msg.deleteCharAt(msg.length() - 1);
                }
                plotter.addValues(plot, polarHrData);
                textViewHR.setText(msg.toString());
            }

            @Override
            public void polarFtpFeatureReady(String s) {
                Log.d(TAG, "Polar FTP ready " + s);
            }
        });
        Log.d(TAG, "onCreate: connectToPolarDevice: DEVICE_ID=" + DEVICE_ID);
        api.connectToPolarDevice(DEVICE_ID);

        long now = new Date().getTime();
        plotter = new TimePlotter(this, DURATION, "HR/RR", Color.RED,
                Color.BLUE, false);
        plotter.setListener(this);

        plot.addSeries(plotter.getHrSeries(), plotter.getHrFormatter());
        plot.addSeries(plotter.getRrSeries(), plotter.getRrFormatter());
        plot.setRangeBoundaries(50, 100,
                BoundaryMode.AUTO);
        plot.setDomainBoundaries(now - DURATION, now, BoundaryMode.FIXED);
        plot.setRangeStep(StepMode.INCREMENT_BY_VAL, 10);
        plot.setDomainStep(StepMode.INCREMENT_BY_VAL, DURATION / 6.);
        // Make left labels be an integer (no decimal places)
        plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).
                setFormat(new DecimalFormat("#"));
        // These don't seem to have an effect
        plot.setLinesPerRangeLabel(2);
        plot.setTitle(getString(R.string.hr_title, DURATION / 60000));

//        PanZoom.attach(plot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom
// .STRETCH_HORIZONTAL);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        api.shutDown();
    }

    public void update() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                plot.redraw();
            }
        });
    }
}
