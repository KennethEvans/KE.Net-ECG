package net.kenevans.polar.polarecg;

import android.graphics.Color;
import android.util.Log;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PanZoom;
import com.androidplot.xy.RectRegion;
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
    private final ECGActivity activity;
    private final XYPlot mPlot;

    private final boolean mPlotHr1 = true;
    private final boolean mPlotRr1 = true;
    private final boolean mPlotHr2 = true;
    private final boolean mPlotRr2 = true;
    private double mPlotStartTime = Double.NaN;
    /**
     * This is the last time a value was added to the plot. Used to set the
     * domain and range boundaries.
     **/
    private double mLastTime = Double.NaN;
    private final long mDomainInterval = 1 * 60000;  // 1 min
    private final RunningMax mRunningMax1 = new RunningMax(50);
    private final RunningMax mRunningMax2 = new RunningMax(50);

    private double mStartRrTime = Double.NEGATIVE_INFINITY;
    private double mLastRrTime;
    private double mLastUpdateTime;
    private double mTotalRrTime;

    private static final double RR_SCALE = .1;  // to 100 ms to use same axis
    //    private static final SimpleDateFormat X_AXIS_DATE_FORMAT = new
    //    SimpleDateFormat(
//            "HH:mm", Locale.US);
    private static final SimpleDateFormat X_AXIS_DATE_FORMAT =
            new SimpleDateFormat("HH:mm:ss", Locale.US);

    private XYSeriesFormatter<XYRegionFormatter> hrFormatter1;
    private XYSeriesFormatter<XYRegionFormatter> rrFormatter1;
    private SimpleXYSeries hrSeries1;
    private SimpleXYSeries rrSeries1;

    private XYSeriesFormatter<XYRegionFormatter> hrFormatter2;
    private XYSeriesFormatter<XYRegionFormatter> rrFormatter2;
    private SimpleXYSeries hrSeries2;
    private SimpleXYSeries rrSeries2;

    public List<HrRrData> mHrRrList1 = new ArrayList<>();
    public List<HrRrData> mHrRrList2 = new ArrayList<>();

    public HRPlotter(ECGActivity activity, XYPlot mPlot,
                     String title, boolean showVertices) {
        Log.d(TAG, this.getClass().getSimpleName() + " ECGPlotter CTOR");
        // This is the activity, needed for resources
        this.activity = activity;
        this.mPlot = mPlot;

        setupPlot();
    }

    /**
     * Sets the plot parameters. Calls update when done.
     */
    public void setupPlot() {
        Log.d(TAG, this.getClass().getSimpleName() + " setupPlot");
        Log.d(TAG, this.getClass().getSimpleName() + ": createPlot");
        hrFormatter1 = new LineAndPointFormatter(Color.RED,
                null, null, null);
        hrFormatter1.setLegendIconEnabled(false);
        rrFormatter1 = new LineAndPointFormatter(Color.rgb(0, 0x99, 0xFF),
                null, null, null);
        rrFormatter1.setLegendIconEnabled(false);

        hrFormatter2 = new LineAndPointFormatter(Color.rgb(0xFF, 0x88, 0xAA),
                null, null, null);
        hrFormatter2.setLegendIconEnabled(false);
        rrFormatter2 = new LineAndPointFormatter(Color.rgb(0, 0xBF, 0xFF),
                null, null, null);
        rrFormatter2.setLegendIconEnabled(false);

        // Numbers are only used for BoundaryMode.Fixed
//        mPlot.setRangeBoundaries(0, 0, BoundaryMode.AUTO);
//        mPlot.setDomainBoundaries(0, 0, BoundaryMode.AUTO);
        mPlot.setRangeBoundaries(0, 140, BoundaryMode.FIXED);
        long time0 = new Date().getTime();
        long time1 = time0 + mDomainInterval;
        mPlot.setDomainBoundaries(time0, time1, BoundaryMode.FIXED);
        // Range labels will increment by 10
        mPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, 40);
//        mPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 60000); // 1 min
        mPlot.setDomainStep(StepMode.SUBDIVIDE, 5);
        // Make left labels be an integer (no decimal places)
        mPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).
                setFormat(new DecimalFormat("#"));
        mPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat(new Format() {
            private final SimpleDateFormat dateFormat = X_AXIS_DATE_FORMAT;

            @Override
            public StringBuffer format(Object obj,
                                       @NonNull StringBuffer toAppendTo,
                                       @NonNull FieldPosition pos) {
                long time = Math.round(((Number) obj).doubleValue());
                return dateFormat.format(time, toAppendTo, pos);
            }

            @Override
            public Object parseObject(String source,
                                      @NonNull ParsePosition pos) {
                return null;
            }
        });

//        // This adds sub-grid lines in the default color 180,180,180 (#646464)
//        mPlot.setLinesPerRangeLabel(2);
//        // No resource to set this, and this makes them disappear
//        mPlot.getGraph().setRangeSubGridLinePaint(new Paint(Color.rgb(90, 90,
//                90)));

        if (mPlotHr1) {
            hrSeries1 = new SimpleXYSeries("HR1");
            mPlot.addSeries(hrSeries1, hrFormatter1);
        } else {
            hrSeries1 = null;
        }
        if (mPlotRr1) {
            rrSeries1 = new SimpleXYSeries("RR1");
            mPlot.addSeries(rrSeries1, rrFormatter1);
        } else {
            rrSeries1 = null;
        }
        if (mPlotHr2) {
            hrSeries2 = new SimpleXYSeries("HR2");
            mPlot.addSeries(hrSeries2, hrFormatter2);
        } else {
            hrSeries2 = null;
        }
        if (mPlotRr2) {
            rrSeries2 = new SimpleXYSeries("RR2");
            mPlot.addSeries(rrSeries2, rrFormatter2);
        } else {
            rrSeries2 = null;
        }

        // Pan and Zoom
        PanZoom.attach(mPlot, PanZoom.Pan.BOTH, PanZoom.Zoom.STRETCH_BOTH);
    }

    @SuppressWarnings("ConstantConditions")
    public void addValues1(double time, double hr, List<Integer> rrsMs) {
//        Log.d(TAG, this.getClass().getSimpleName() + ": addHrValues: time="
//                + mDateFormatSec.format(time) + " hr=" + hr + " hrSize=" +
//                hrSeries1.size());
        if (mPlotHr1 || mPlotRr1) {
            mHrRrList1.add(new HrRrData(time, hr, rrsMs));
//            StringBuilder sb = new StringBuilder();
//            sb.append("HRPlotter: addValues1");
//            sb.append(" time=");
//            sb.append(X_AXIS_DATE_FORMAT.format(new Date(Math.round(time))));
//            sb.append(" hr=").append(Math.round(hr)).append(" rr=");
//            for (int rr : rrsMs) {
//                sb.append(rr).append(" ");
//            }
//            Log.d(TAG, sb.toString());
            if (Double.isNaN(mPlotStartTime)) mPlotStartTime = time;
            if (Double.isNaN(mLastTime)) {
                mLastTime = time;
            } else if (time > mLastTime) {
                mLastTime = time;
            }
            if (Double.isNaN(mPlotStartTime)) mPlotStartTime = time;
            if (Double.isNaN(mLastTime)) {
                mLastTime = time;
            } else if (time > mLastTime) {
                mLastTime = time;
            }
        }

        // HR
        if (mPlotHr1) {
            mRunningMax1.add(hr);
            hrSeries1.addLast(time, hr);
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
                mStartRrTime = mLastRrTime = mLastUpdateTime = time - totalRR;
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
                rrSeries1.addLast(tVals[i], rr);
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
            mHrRrList2.add(new HrRrData(time, hr, rr));
//            Log.d(TAG, "HRPlotter: addValues2"
//                    + " time=" + X_AXIS_DATE_FORMAT.format(new Date(Math.round(time)))
//                    + " hr=" + Math.round(hr)
//                    + " rr=" + Math.round(rr));
            if (Double.isNaN(mPlotStartTime)) mPlotStartTime = time;
            if (Double.isNaN(mLastTime)) {
                mLastTime = time;
            } else if (time > mLastTime) {
                mLastTime = time;
            }
        }

        if (mPlotHr2) {
            mRunningMax2.add(hr);
            hrSeries2.addLast(time, hr);
        }
        if (mPlotRr2) {
            mRunningMax2.add(RR_SCALE * rr);
            rrSeries2.addLast(time, RR_SCALE * rr);
        }
    }

    /**
     * Gets info about the view.
     */
    private String getPlotInfo() {
        final String LF = "\n";
        StringBuilder sb = new StringBuilder();
        if (mPlot == null) {
            sb.append("View is null");
            return sb.toString();
        }
        sb.append("Title=").append(mPlot.getTitle().getText()).append(LF);
        sb.append("Range Title=").append(mPlot.getRangeTitle().getText()).append(LF);
        sb.append("Domain Title=").append(mPlot.getDomainTitle().getText()).append(LF);
        sb.append("Range Origin=").append(mPlot.getRangeOrigin()).append(LF);
        long timeVal = mPlot.getDomainOrigin().longValue();
        Date date = new Date(timeVal);
        sb.append("Domain Origin=").append(date.toString()).append(LF);
        sb.append("Range Step Value=").append(mPlot.getRangeStepValue()).append(LF);
        sb.append("Domain Step Value=").append(mPlot.getDomainStepValue()).append(LF);
        sb.append("Graph Width=").append(mPlot.getGraph().getSize().getWidth().getValue()).append(LF);
        sb.append("Graph Height=").append(mPlot.getGraph().getSize().getHeight().getValue()).append(LF);
        if (hrSeries1 != null) {
            if (hrSeries1.getxVals() != null) {
                sb.append("hrSeries1 Size=").append(hrSeries1.getxVals().size()).append(LF);
            }
        } else {
            sb.append("hrSeries1=Null").append(LF);
        }
        if (rrSeries1 != null) {
            if (rrSeries1.getxVals() != null) {
                sb.append("rrSeries1 Size=").append(rrSeries1.getxVals().size()).append(LF);
            }
        } else {
            sb.append("rrSeries1=Null").append(LF);
        }

        if (hrSeries2 != null) {
            if (hrSeries2.getxVals() != null) {
                sb.append("hrSeries2 Size=").append(hrSeries2.getxVals().size()).append(LF);
            }
        } else {
            sb.append("hrSeries2=Null").append(LF);
        }
        if (rrSeries2 != null) {
            if (rrSeries2.getxVals() != null) {
                sb.append("rrSeries2 Size=").append(rrSeries2.getxVals().size()).append(LF);
            }
        } else {
            sb.append("rrSeries2=Null").append(LF);
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

    public void setDomainRangeBoundaries() {
        double max = Math.max(mRunningMax1.max(), mRunningMax2.max());
        if (max < 60) max = 60;
//        Log.d(TAG, this.getClass().getSimpleName() + "update: mPlotStartTime="
//                + mPlotStartTime + " mlastTime=" + mLastTime
//                + " max=" + max);
        if (!Double.isNaN(mLastTime) && !Double.isNaN(mPlotStartTime)
                && mLastTime - mPlotStartTime > mDomainInterval) {
            mPlot.setDomainBoundaries(mLastTime - mDomainInterval, mLastTime,
                    BoundaryMode.FIXED);
        }
        mPlot.setRangeBoundaries(0, Math.ceil(max + 10), BoundaryMode.FIXED);
//        RectRegion rgn= mPlot.getOuterLimits();
//        Log.d(TAG,"OuterLimits="  + rgn.getMinX() + "," + rgn.getMaxX());
//        mPlot.getOuterLimits().set(mPlotStartTime, mLastTime,
//                0, Math.ceil(max + 10));
    }

    public void setOuterLimits() {
        mPlot.getOuterLimits().setMinX(mPlotStartTime);
        mPlot.getOuterLimits().setMaxX(mLastTime);
    }

    /**
     * Sets the domain and range boundaries and the does update.
     */
    public void fullUpdate() {
        setDomainRangeBoundaries();
        update();
    }

    /**
     * Updates the plot. Runs on the UI thread.
     */
    public void update() {
        activity.runOnUiThread(mPlot::redraw);
    }

    public static class HrRrData {
        // THis is the same formatter as sessionSaveFormatter in Bluetooth
        // Cardiac Monitor.
        private static final SimpleDateFormat sdf = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        private String time;
        private String hr;
        private String rr;

        public HrRrData(double time, double hr, List<Integer> rrsMs) {
            this.time = sdf.format(new Date(Math.round(time)));
            this.hr = String.format(Locale.US, "%.0f", hr);
            StringBuilder sb = new StringBuilder();
            for (Integer rr : rrsMs) {
                sb.append(rr);
            }
            this.rr = sb.toString().trim();
        }

        public HrRrData(double time, double hr, double rr) {
            this.time = sdf.format(new Date(Math.round(time)));
            this.hr = String.format(Locale.US, "%.0f", hr);
            this.rr = String.format(Locale.US, "%.0f", rr);
        }

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
    }
}
