package com.polar.polarsdkhrcomparedemo;

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
    private static final int DURATION = 60000;  // In ms
    private XYPlot plot;
    private TimePlotter plotter1, plotter2;

    TextView textViewHR1, textViewRR1, textViewFW1;
    TextView textViewHR2, textViewRR2, textViewFW2;
    private String TAG = "Polar_HRActivity";
    public PolarBleApi api1, api2;
    private Disposable ecgDisposable = null;
    private Context classContext = this;
    private String DEVICE_ID_1, DEVICE_ID_2;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hr);
        DEVICE_ID_1 = getIntent().getStringExtra("id1");
        DEVICE_ID_2 = getIntent().getStringExtra("id2");
        textViewHR1 = findViewById(R.id.hrinfo1);
        textViewRR1 = findViewById(R.id.rrinfo1);
        textViewFW1 = findViewById(R.id.fw1);
        textViewHR2 = findViewById(R.id.hrinfo2);
        textViewRR2 = findViewById(R.id.rrinfo2);
        textViewFW2 = findViewById(R.id.fw2);

        plot = findViewById(R.id.plot);

        // Device 1
        api1 = PolarBleApiDefaultImpl.defaultImplementation(this,
                PolarBleApi.FEATURE_BATTERY_INFO |
                        PolarBleApi.FEATURE_DEVICE_INFO |
                        PolarBleApi.FEATURE_HR);
        api1.setPolarFilter(false);
        api1.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG, "BluetoothStateChanged " + b);
            }

            @Override
            public void polarDeviceConnected(PolarDeviceInfo s) {
                Log.d(TAG, "Device connected " + s.deviceId);
                String msg = s.name + "\n" + s.deviceId;
                textViewFW1.append("\n" + msg);

                Toast.makeText(classContext,
                        R.string.connected + " " + s.deviceId,
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
                    textViewFW1.append("\n" + msg);
                }
            }

            @Override
            public void batteryLevelReceived(String s, int i) {
                Log.d(TAG, "Battery level 1 " + s + " " + i);
                String msg = "Battery level: " + i;
//                Toast.makeText(classContext, msg, Toast.LENGTH_LONG).show();
                textViewFW1.append("\n" + msg);
            }

            @Override
            public void hrNotificationReceived(String s,
                                               PolarHrData polarHrData) {
                Log.d(TAG, "HR1 " + polarHrData.hr);
                List<Integer> rrsMs = polarHrData.rrsMs;
                StringBuilder msg = new StringBuilder();
                for (int i : rrsMs) {
                    msg.append(i).append(",");
                }
                if (msg.toString().endsWith(",")) {
                    msg.deleteCharAt(msg.length() - 1);
                }
                plotter1.addValues(plot, polarHrData);
                msg.append("\n").append(plotter1.getRrInfo());
                textViewHR1.setText(String.valueOf(polarHrData.hr));
                textViewRR1.setText(msg.toString());
            }

            @Override
            public void polarFtpFeatureReady(String s) {
                Log.d(TAG, "Polar FTP ready " + s);
            }
        });
        if (DEVICE_ID_1 != null && !DEVICE_ID_1.isEmpty()) {
            Log.d(TAG, "onCreate: connectToPolarDevice: DEVICE_ID_1="
                    + DEVICE_ID_1);
            api1.connectToPolarDevice(DEVICE_ID_1);
        }

        // Device 2
        api2 = PolarBleApiDefaultImpl.defaultImplementation(this,
                PolarBleApi.FEATURE_BATTERY_INFO |
                        PolarBleApi.FEATURE_DEVICE_INFO |
                        PolarBleApi.FEATURE_HR);
        api2.setPolarFilter(false);
        api2.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG, "BluetoothStateChanged " + b);
            }

            @Override
            public void polarDeviceConnected(PolarDeviceInfo s) {
                Log.d(TAG, "Device connected " + s.deviceId);
                String msg = s.name + "\n" + s.deviceId;
                textViewFW2.append("\n" + msg);

                Toast.makeText(classContext,
                        R.string.connected + " " + s.deviceId,
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
                    textViewFW2.append("\n" + msg);
                }
            }

            @Override
            public void batteryLevelReceived(String s, int i) {
                Log.d(TAG, "Battery level 2 " + s + " " + i);
                String msg = "Battery level: " + i;
//                Toast.makeText(classContext, msg, Toast.LENGTH_LONG).show();
                textViewFW2.append("\n" + msg);
            }

            @Override
            public void hrNotificationReceived(String s,
                                               PolarHrData polarHrData) {
                Log.d(TAG, "HR2 " + polarHrData.hr);
                List<Integer> rrsMs = polarHrData.rrsMs;
                StringBuilder msg = new StringBuilder();
                for (int i : rrsMs) {
                    msg.append(i).append(",");
                }
                if (msg.toString().endsWith(",")) {
                    msg.deleteCharAt(msg.length() - 1);
                }
                plotter2.addValues(plot, polarHrData);
                msg.append("\n").append(plotter2.getRrInfo());
                textViewHR2.setText(String.valueOf(polarHrData.hr));
                textViewRR2.setText(msg.toString());
            }

            @Override
            public void polarFtpFeatureReady(String s) {
                Log.d(TAG, "Polar FTP ready " + s);
            }
        });
        if (DEVICE_ID_2 != null && !DEVICE_ID_2.isEmpty()) {
            Log.d(TAG, "onCreate: connectToPolarDevice: DEVICE_ID_2="
                    + DEVICE_ID_2);
            api2.connectToPolarDevice(DEVICE_ID_2);
        }

        long now = new Date().getTime();

        plotter1 = new TimePlotter(this, DURATION, "HR1/RR1",
                Color.RED, Color.BLUE, true);
        plotter1.setListener(this);
        plotter2 = new TimePlotter(this, DURATION, "HR2/RR2",
                Color.rgb(0xFF, 0x88, 0xAA),
                Color.rgb(0x88, 0, 0x88), true);
        plotter2.setListener(this);

        plot.addSeries(plotter1.getHrSeries(), plotter1.getHrFormatter());
        plot.addSeries(plotter2.getHrSeries(), plotter2.getHrFormatter());
        plot.addSeries(plotter1.getRrSeries(), plotter1.getRrFormatter());
        plot.addSeries(plotter2.getRrSeries(), plotter2.getRrFormatter());
        plot.setRangeBoundaries(50, 100,
                BoundaryMode.AUTO);
        plot.setDomainBoundaries(now - DURATION, now, BoundaryMode.FIXED);
        plot.setRangeStep(StepMode.INCREMENT_BY_VAL, 10);
        plot.setDomainStep(StepMode.INCREMENT_BY_VAL, DURATION / 6.);
        // Make left labels be an integer (no decimal places)
        plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).
                setFormat(new DecimalFormat("#"));
        plot.setLinesPerRangeLabel(2);
        plot.setTitle(getString(R.string.hr_title, DURATION / 60000));

//        PanZoom.attach(plot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom
// .STRETCH_HORIZONTAL);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        api1.shutDown();
        api2.shutDown();
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
