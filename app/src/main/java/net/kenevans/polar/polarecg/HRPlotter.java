package net.kenevans.polar.polarecg;

import android.graphics.Color;
import android.util.Log;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PanZoom;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYRegionFormatter;
import com.androidplot.xy.XYSeriesFormatter;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;

public class HRPlotter implements IConstants, IQRSConstants {
    private ECGActivity mActivity;
    private XYPlot mPlot;

    private final boolean mPlotHr1 = true;
    private final boolean mPlotRr1 = true;
    private final boolean mPlotHr2 = true;
    private final boolean mPlotRr2 = true;

    /**
     * This is the last time a value was added to the plot. Used to set the
     * domain and range boundaries.
     **/
    private double mLastTime = Double.NaN;
    private double mStartTime = Double.NaN;
    private double mStartRrTime = Double.NEGATIVE_INFINITY;

    private RunningMax mRunningMax1 = new RunningMax(50);
    private RunningMax mRunningMax2 = new RunningMax(50);

    private double mLastRrTime;
    private double mLastUpdateTime;
    private double mTotalRrTime;
    private static final double RR_SCALE = .1;  // to 100 ms to use same axis
    private static final SimpleDateFormat X_AXIS_DATE_FORMAT =
            new SimpleDateFormat("HH:mm:ss", Locale.US);

    private XYSeriesFormatter<XYRegionFormatter> mHrFormatter1;
    private XYSeriesFormatter<XYRegionFormatter> mRrFormatter1;
    public SimpleXYSeries mHrSeries1;
    public SimpleXYSeries mRrSeries1;

    private XYSeriesFormatter<XYRegionFormatter> mHrFormatter2;
    private XYSeriesFormatter<XYRegionFormatter> mRrFormatter2;
    public SimpleXYSeries mHrSeries2;
    public SimpleXYSeries mRrSeries2;

    public List<HrRrSessionData> mHrRrList1 = new ArrayList<>();
    public List<HrRrSessionData> mHrRrList2 = new ArrayList<>();

    /**
     * CTOR that just sets the plot.
     *
     * @param plot The XYPlot.
     */
    public HRPlotter(XYPlot plot) {
        this.mPlot = plot;
        // Don't do anything else
    }

    public HRPlotter(ECGActivity activity, XYPlot plot) {
        Log.d(TAG, this.getClass().getSimpleName() + " ECGPlotter CTOR");
        // This is the mActivity, needed for resources
        this.mActivity = activity;
        this.mPlot = plot;

        if (mPlotHr1) {
            mHrFormatter1 = new LineAndPointFormatter(Color.RED,
                    null, null, null);
            mHrFormatter1.setLegendIconEnabled(false);
            mHrSeries1 = new SimpleXYSeries("HR1");
        }
        if (mPlotRr1) {
            mRrFormatter1 = new LineAndPointFormatter(Color.rgb(0, 0x99, 0xFF),
                    null, null, null);
            mRrFormatter1.setLegendIconEnabled(false);
            mRrSeries1 = new SimpleXYSeries("RR1");
        }
        if (mPlotHr2) {
            mHrFormatter2 = new LineAndPointFormatter(Color.rgb(0xFF, 0x88,
                    0xAA),
                    null, null, null);
            mHrFormatter2.setLegendIconEnabled(false);
            mHrSeries2 = new SimpleXYSeries("HR2");
        }
        if (mPlotRr2) {
            mRrFormatter2 = new LineAndPointFormatter(Color.rgb(0, 0xBF, 0xFF),
                    null, null, null);
            mRrFormatter2.setLegendIconEnabled(false);
            mRrSeries2 = new SimpleXYSeries("RR2");
        }

        mPlot.addSeries(mHrSeries1, mHrFormatter1);
        mPlot.addSeries(mRrSeries1, mRrFormatter1);
        mPlot.addSeries(mHrSeries2, mHrFormatter2);
        mPlot.addSeries(mRrSeries2, mRrFormatter2);
        setupPlot();
    }

    /**
     * Get a new QHRPLotter instance, using the given XYPlot but other values
     * from the current one. Use for replacing the current plotter.
     *
     * @param plot The XYPLot.
     * @return The new instance.
     */
    public HRPlotter getNewInstance(XYPlot plot) {
        HRPlotter newPlotter = new HRPlotter(plot);
        newPlotter.mPlot = plot;
        newPlotter.mActivity = this.mActivity;
        newPlotter.mLastTime = this.mLastTime;
        newPlotter.mStartTime = this.mStartTime;
        newPlotter.mStartRrTime = this.mStartRrTime;
        newPlotter.mRunningMax1 = this.mRunningMax1;
        newPlotter.mRunningMax2 = this.mRunningMax2;
        newPlotter.mHrRrList1 = this.mHrRrList1;
        newPlotter.mHrRrList2 = this.mHrRrList2;
        newPlotter.mLastRrTime = this.mLastRrTime;
        newPlotter.mLastUpdateTime = this.mLastUpdateTime;
        newPlotter.mTotalRrTime = this.mTotalRrTime;

        newPlotter.mHrFormatter1 = this.mHrFormatter1;
        newPlotter.mHrSeries1 = this.mHrSeries1;

        newPlotter.mRrFormatter1 = this.mRrFormatter1;
        newPlotter.mRrSeries1 = this.mRrSeries1;

        newPlotter.mHrFormatter2 = this.mHrFormatter2;
        newPlotter.mHrSeries2 = this.mHrSeries2;

        newPlotter.mRrFormatter2 = this.mRrFormatter2;
        newPlotter.mRrSeries2 = this.mRrSeries2;

        newPlotter.mPlot.addSeries(mHrSeries1, mHrFormatter1);
        newPlotter.mPlot.addSeries(mRrSeries1, mRrFormatter1);
        newPlotter.mPlot.addSeries(mHrSeries2, mHrFormatter2);
        newPlotter.mPlot.addSeries(mRrSeries2, mRrFormatter2);
        newPlotter.setupPlot();

        return newPlotter;
    }

    /**
     * Sets the plot parameters. Calls update when done.
     */
    public void setupPlot() {
        Log.d(TAG, this.getClass().getSimpleName() + " setupPlot");

        try {
            // Set the domain and range boundaries
            updateDomainRangeBoundaries();

            // Range labels will increment by 10
            mPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, 40);
//        mPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 60000); // 1 min
            mPlot.setDomainStep(StepMode.SUBDIVIDE, 5);
            mPlot.getGraph().setLineLabelEdges(XYGraphWidget.Edge.BOTTOM,
                    XYGraphWidget.Edge.LEFT);
            // Make left labels be an integer (no decimal places)
            mPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).
                    setFormat(new DecimalFormat("#"));
            // Set x axis labeling to be time
            mPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM)
                    .setFormat(new Format() {
                        private final SimpleDateFormat dateFormat =
                                X_AXIS_DATE_FORMAT;

                        @Override
                        public StringBuffer format(Object obj,
                                                   @NonNull StringBuffer toAppendTo,
                                                   @NonNull FieldPosition pos) {
                            long time =
                                    Math.round(((Number) obj).doubleValue());
                            return dateFormat.format(time, toAppendTo, pos);
                        }

                        @Override
                        public Object parseObject(String source,
                                                  @NonNull ParsePosition pos) {
                            return null;
                        }
                    });

//        // Allow panning
//        PanZoom.attach(mPlot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom.NONE);

            update();
        } catch (Exception ex) {
            String msg = "Error in HRPLotter.setupPLot:\n"
                    + "isLaidOut=" + mPlot.isLaidOut()
                    + " width=" + mPlot.getWidth()
                    + " height=" + mPlot.getHeight();
            Utils.excMsg(mActivity, msg, ex);
            Log.e(TAG, msg, ex);
        }
    }

    @SuppressWarnings("ConstantConditions")
    public void addValues1(double time, double hr, List<Integer> rrsMs) {
//        Log.d(TAG, this.getClass().getSimpleName() + ": addHrValues: time="
//                + mDateFormatSec.format(time) + " hr=" + hr + " hrSize=" +
//                mHrSeries1.size());
        if (mPlotHr1 || mPlotRr1) {
            mHrRrList1.add(new HrRrSessionData(time, hr, rrsMs));
//            StringBuilder sb = new StringBuilder();
//            sb.append("HRPlotter: addValues1");
//            sb.append(" time=");
//            sb.append(X_AXIS_DATE_FORMAT.format(new Date(Math.round(time))));
//            sb.append(" hr=").append(Math.round(hr)).append(" rr=");
//            for (int rr : rrsMs) {
//                sb.append(rr).append(" ");
//            }
//            Log.d(TAG, sb.toString());
            if (Double.isNaN(mStartTime)) mStartTime = time;
            if (Double.isNaN(mLastTime)) {
                mLastTime = time;
            } else if (time > mLastTime) {
                mLastTime = time;
            }
        }

        // HR
        if (mPlotHr1) {
            mRunningMax1.add(hr);
            mHrSeries1.addLast(time, hr);
        }

        // RR
        int nRrVals = rrsMs.size();
        if (mPlotRr1 && nRrVals > 0) {
            double[] tVals = new double[nRrVals];
            Integer[] rrVals = new Integer[nRrVals];
            rrVals = rrsMs.toArray(rrVals);
            // Find the sum of the RR intervals
            double totalRR = 0;
            for (int i = 0; i < nRrVals; i++) {
                totalRR += rrVals[i];
            }
            // First time
            if (Double.isInfinite(mStartRrTime)) {
                mStartRrTime = mLastRrTime = mLastUpdateTime =
                        time - totalRR;
                mTotalRrTime = 0;
            }
            mTotalRrTime += totalRR;
//        Log.d(TAG, "lastRrTime=" + mLastRrTime
//                + " totalRR=" + totalRR
//                + " elapsed=" + (mLastRrTime - mStartRrTime)
//                + " totalRrTime=" + mTotalRrTime);

            double rr;
            double t = mLastRrTime;
            for (int i = 0; i < nRrVals; i++) {
                rr = rrVals[i];
                t += rr;
                tVals[i] = t;
            }
            // Keep them in this interval
            if (tVals[0] < mLastUpdateTime) {
                double deltaT = mLastUpdateTime = tVals[0];
                t += deltaT;
                for (int i = 0; i < nRrVals; i++) {
                    tVals[i] += deltaT;
                }
            }
            // Keep them from being in the future
            if (t > time) {
                double deltaT = t - time;
                for (int i = 0; i < nRrVals; i++) {
                    tVals[i] -= deltaT;
                }
            }
            // Add to the series
            for (int i = 0; i < nRrVals; i++) {
                rr = RR_SCALE * rrVals[i];
                mRunningMax1.add(rr);
                mRrSeries1.addLast(tVals[i], rr);
                mLastRrTime = tVals[i];
            }
            mLastUpdateTime = time;
        }
    }

    @SuppressWarnings("ConstantConditions")
    public void addValues2(double time, double hr, double rr) {
//        Log.d(TAG, this.getClass().getSimpleName() + ": addValues2: time="
//                + mDateFormatSec.format(time) + " hr=" + hr + " rr="
//                + RR_SCALE * rr);
        if (mPlotHr2 || mPlotRr2) {
            mHrRrList2.add(new HrRrSessionData(time, hr, rr));
//            Log.d(TAG, "HRPlotter: addValues2"
//                    + " time=" + X_AXIS_DATE_FORMAT.format(new Date(Math
//                    .round(time)))
//                    + " hr=" + Math.round(hr)
//                    + " rr=" + Math.round(rr));
            if (Double.isNaN(mStartTime)) mStartTime = time;
            if (Double.isNaN(mLastTime)) {
                mLastTime = time;
            } else if (time > mLastTime) {
                mLastTime = time;
            }
        }

        if (mPlotHr2) {
            mRunningMax2.add(hr);
            mHrSeries2.addLast(time, hr);
        }
        if (mPlotRr2) {
            mRunningMax2.add(RR_SCALE * rr);
            mRrSeries2.addLast(time, RR_SCALE * rr);
        }
    }

    /**
     * Gets info about the view.
     */
    @SuppressWarnings("unused")
    private String getPlotInfo() {
        final String LF = "\n";
        StringBuilder sb = new StringBuilder();
        if (mPlot == null) {
            sb.append("Plot is null");
            return sb.toString();
        }
        sb.append("Title=").append(mPlot.getTitle().getText()).append(LF);
        sb.append("Range Title=").append(mPlot.getRangeTitle().getText()).append(LF);
        sb.append("Domain Title=").append(mPlot.getDomainTitle().getText()).append(LF);
        sb.append("Range Origin=").append(mPlot.getRangeOrigin()).append(LF);
        long timeVal = mPlot.getDomainOrigin().longValue();
        Date date = new Date(timeVal);
        sb.append("Domain Origin=").append(date).append(LF);
        sb.append("Range Step Value=").append(mPlot.getRangeStepValue()).append(LF);
        sb.append("Domain Step Value=").append(mPlot.getDomainStepValue()).append(LF);
        sb.append("Graph Width=").append(mPlot.getGraph().getSize().getWidth().getValue()).append(LF);
        sb.append("Graph Height=").append(mPlot.getGraph().getSize().getHeight().getValue()).append(LF);
        sb.append("TotalRrTime=").append(mTotalRrTime).append(LF);
        if (mHrSeries1 != null) {
            if (mHrSeries1.getxVals() != null) {
                sb.append("mHrSeries1 Size=")
                        .append(mHrSeries1.getxVals().size()).append(LF);
            }
        } else {
            sb.append("mHrSeries1=Null").append(LF);
        }
        if (mRrSeries1 != null) {
            if (mRrSeries1.getxVals() != null) {
                sb.append("mRrSeries1 Size=")
                        .append(mRrSeries1.getxVals().size()).append(LF);
            }
        } else {
            sb.append("mRrSeries1=Null").append(LF);
        }

        if (mHrSeries2 != null) {
            if (mHrSeries2.getxVals() != null) {
                sb.append("mHrSeries2 Size=")
                        .append(mHrSeries2.getxVals().size()).append(LF);
            }
        } else {
            sb.append("mHrSeries2=Null").append(LF);
        }
        if (mRrSeries2 != null) {
            if (mRrSeries2.getxVals() != null) {
                sb.append("mRrSeries2 Size=")
                        .append(mRrSeries2.getxVals().size()).append(LF);
            }
        } else {
            sb.append("mRrSeries2=Null").append(LF);
        }
        return sb.toString();
    }

//    public String getRrInfo() {
//        double elapsed = MS_TO_SEC * (mLastRrTime - mStartRrTime);
//        double total = MS_TO_SEC * mTotalRrTime;
//        double ratio = total / elapsed;
//        return "Tot=" + String.format(Locale.US, "%.2f s", elapsed)
//                + " RR=" + String.format(Locale.US, "%.2f s", total)
//                + " (" + String.format(Locale.US, "%.2f", ratio) + ")";
//    }

    public void updateDomainRangeBoundaries() {
        double max = Math.max(mRunningMax1.max(), mRunningMax2.max());
        if (Double.isNaN(max) || max < 60) max = 60;
//        Log.d(TAG, this.getClass().getSimpleName() +
//        "updateDomainRangeBoundaries: mStartTime="
//                + mStartTime + " mlastTime=" + mLastTime
//                + " max=" + max);
        if (!Double.isNaN(mLastTime) && !Double.isNaN(mStartTime)) {
            if (mLastTime - mStartTime > HR_PLOT_DOMAIN_INTERVAL) {
                mPlot.setDomainBoundaries(mLastTime - HR_PLOT_DOMAIN_INTERVAL,
                        mLastTime, BoundaryMode.FIXED);
            } else {
                mPlot.setDomainBoundaries(mStartTime,
                        mStartTime + HR_PLOT_DOMAIN_INTERVAL,
                        BoundaryMode.FIXED);
            }
        } else {
            long time0 = new Date().getTime();
            long time1 = time0 + HR_PLOT_DOMAIN_INTERVAL;
            mPlot.setDomainBoundaries(time0, time1, BoundaryMode.FIXED);
        }
        Number upperBoundary = Math.min(Math.ceil(max + 10), 200);
        mPlot.setRangeBoundaries(0, upperBoundary, BoundaryMode.FIXED);
//        RectRegion rgn= mPlot.getOuterLimits();
//        Log.d(TAG,"OuterLimits="  + rgn.getMinX() + "," + rgn.getMaxX());
//        mPlot.getOuterLimits().set(mStartTime, mLastTime,
//                0, Math.ceil(max + 10));
    }

//    public void setOuterLimits() {
//        mLock.writeLock().lock();
//        try {
//            if (!Double.isNaN(mLastTime) && Double.isNaN(mLastTime)) {
//                mPlot.getOuterLimits().setMinX(mStartTime);
//                mPlot.getOuterLimits().setMaxX(mLastTime);
//            } else {
//                mPlot.getOuterLimits().setMinX(0);
//                mPlot.getOuterLimits().setMaxX(1);
//            }
//        } finally {
//            mLock.writeLock().unlock();
//        }
//    }

    /**
     * Updates the plot. Runs on the UI thread.
     */
    public void update() {
//        Log.d(TAG, "HRPlotter: update: dataList sizes=" + mHrRrList1.size()
//                + "," + mHrRrList2.size());
        mActivity.runOnUiThread(mPlot::redraw);
    }

    /**
     * Sets the domain and range boundaries and the does update.
     */
    public void fullUpdate() {
        updateDomainRangeBoundaries();
        update();
    }

    /**
     * Set panning on or off.
     *
     * @param on Whether to be on or off (true for on).
     */
    @SuppressWarnings("unused")
    public void setPanning(boolean on) {
        if (on) {
            PanZoom.attach(mPlot, PanZoom.Pan.HORIZONTAL,
                    PanZoom.Zoom.NONE);
        } else {
            PanZoom.attach(mPlot, PanZoom.Pan.NONE, PanZoom.Zoom.NONE);
        }
    }

    /**
     * Clears the plot.
     */
    public void clear() {
        mHrSeries1.clear();
        mRrSeries1.clear();
        mHrSeries2.clear();
        mRrSeries2.clear();
        mLastTime = Double.NaN;
        mStartTime = Double.NaN;
        mRunningMax1 = new RunningMax(50);
        mRunningMax2 = new RunningMax(50);
        long time0 = new Date().getTime();
        long time1 = time0 + HR_PLOT_DOMAIN_INTERVAL;
        mPlot.setDomainBoundaries(time0, time1, BoundaryMode.FIXED);
        update();
    }

    /**
     * Class for handling data to be written to a Session file as used by
     * Bluetooth Cardiac Monitor (BCM) and HxM Monitor.  Note that RR for
     * session data is in units of 1/1024 sec, not ms. THis is the raw unit
     * for RR in the BLE packet.
     */
    public static class HrRrSessionData {
        // This is the same formatter as sessionSaveFormatter in Bluetooth
        // Cardiac Monitor.
        private static final SimpleDateFormat sdf = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        private final String time;
        private final String hr;
        private final String rr;

        public HrRrSessionData(double time, double hr, List<Integer> rrsMs) {
            this.time = sdf.format(new Date(Math.round(time)));
            this.hr = String.format(Locale.US, "%.0f", hr);
            StringBuilder sb = new StringBuilder();
            for (Integer rr : rrsMs) {
                // Convert ms to 1/1024 sec.
                sb.append(Math.round(1.024 * rr)).append(" ");
            }
            this.rr = sb.toString().trim();
        }

        public HrRrSessionData(double time, double hr, double rr) {
            this.time = sdf.format(new Date(Math.round(time)));
            this.hr = String.format(Locale.US, "%.0f", hr);
            // Convert ms to 1/1024 sec.
            this.rr = String.format(Locale.US, "%d", Math.round(1.024 * rr));
        }

        /***
         * Gets a string for writing session files. These have RR in units
         * of 1/1024 sec, not ms.
         * @return The string
         */
        public String getCVSString() {
            return String.format("%s,%s,%s", time, hr, rr);
        }
    }

    public static class RunningMax {
        private final int windowSize;
        List<Double> values = new ArrayList<>();

        public RunningMax(int windowSize) {
            this.windowSize = windowSize;
        }

        public void add(double val) {
            values.add(val);
            while (values.size() > windowSize) {
                values.remove(0);
            }
        }

        public double max() {
            double max = -Double.MAX_VALUE;
            for (double val : values) {
                if (val > max) max = val;
            }
            return max;
        }

        @SuppressWarnings("unused")
        public int size() {
            return values.size();
        }
    }
}
