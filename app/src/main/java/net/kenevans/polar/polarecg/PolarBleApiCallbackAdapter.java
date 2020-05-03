package net.kenevans.polar.polarecg;

import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarHrData;

/**
 * This class implements <code>PolarBleApiCallback</code> and implements all
 * methods with empty bodies.  This allows a callback interested in
 * implementing only a subset of the <code>PolarBleApiCallback</code>
 * interface to extend this class and override only the desired methods.
 */
public abstract class PolarBleApiCallbackAdapter implements PolarBleApiCallback {
    @Override
    public void blePowerStateChanged(boolean b) {
    }

    @Override
    public void polarDeviceConnected(PolarDeviceInfo s) {
    }

    @Override
    public void polarDeviceConnecting(PolarDeviceInfo polarDeviceInfo) {
    }

    @Override
    public void polarDeviceDisconnected(PolarDeviceInfo s) {
    }

    @Override
    public void ecgFeatureReady(String s) {
    }

    @Override
    public void accelerometerFeatureReady(String s) {
    }

    @Override
    public void ppgFeatureReady(String s) {
    }

    @Override
    public void ppiFeatureReady(String s) {
    }

    @Override
    public void biozFeatureReady(String s) {
    }

    @Override
    public void hrFeatureReady(String s) {
    }

    @Override
    public void fwInformationReceived(String s, String s1) {
    }

    @Override
    public void batteryLevelReceived(String s, int i) {
    }

    @Override
    public void hrNotificationReceived(String s,
                                       PolarHrData polarHrData) {
    }

    @Override
    public void polarFtpFeatureReady(String s) {
    }
}
