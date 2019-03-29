package com.polar.polarsdkecghrdemo;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYPlot;

import java.util.List;

import io.reactivex.disposables.Disposable;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarHrData;

public class HRActivity extends AppCompatActivity implements PlotterListener {

    private XYPlot plot;
    private DatePlotter plotter;

    TextView textViewHR, textViewFW;
    private String TAG = "Polar_HRActivity";
    public PolarBleApi api;
    private Disposable ecgDisposable = null;
    private Context classContext = this;
    private String DEVICE_ID;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
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
        api.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG, "BluetoothStateChanged " + b);
            }

            @Override
            public void polarDeviceConnected(PolarDeviceInfo s) {
                Log.d(TAG, "Device connected " + s.deviceId);
                Toast.makeText(classContext, R.string.connected,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void polarDeviceConnecting(PolarDeviceInfo polarDeviceInfo) {

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
            public void fwInformationReceived(String s, String s1) {
                String msg = "Firmware: " + s1.trim();
                Log.d(TAG, "Firmware: " + s + " " + s1.trim());
                textViewFW.append(msg + "\n");
            }

            @Override
            public void batteryLevelReceived(String s, int i) {
                String msg = "ID: " + s + "\nBattery level: " + i;
                Log.d(TAG, "Battery level " + s + " " + i);
//                Toast.makeText(classContext, msg, Toast.LENGTH_LONG).show();
                textViewFW.append(msg + "\n");
            }

            @Override
            public void hrNotificationReceived(String s,
                                               PolarHrData polarHrData) {
                Log.d(TAG, "HR " + polarHrData.hr);
                List<Integer> rrsMs = polarHrData.rrsMs;
                String msg = String.valueOf(polarHrData.hr) + "\n";
                for (int i : rrsMs) {
                    msg += i + ",";
                }
                if (msg.endsWith(",")) {
                    msg = msg.substring(0, msg.length() - 1);
                }
                textViewHR.setText(msg);
                plotter.addValue(polarHrData);
            }

            @Override
            public void polarFtpFeatureReady(String s) {
                Log.d(TAG, "Polar FTP ready " + s);
            }
        });
        api.connectToPolarDevice(DEVICE_ID);

        plotter = new DatePlotter(this, "HR/RR");
        plotter.setListener(this);
//        plotter.getHrFormatter().getLinePaint().setColor(Color.rgb(0, 0,255));

        plot.addSeries(plotter.getHrSeries(), plotter.getHrFormatter());
        plot.addSeries(plotter.getRrSeries(), plotter.getRrFormatter());
        plot.setRangeBoundaries(50, 100, BoundaryMode.AUTO);
        plot.setRangeStep(StepMode.INCREMENT_BY_FIT, 10);
        plot.setDomainBoundaries(0, 500, BoundaryMode.AUTO);
        plot.setLinesPerRangeLabel(2);
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
