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

public class HRPlotter implements IConstants {
    private final ECGActivity activity;
    private final XYPlot mPlot;

    private final boolean mPlotHr = true;
    private final boolean mPlotRr = true;
    private double mPlotStartTime = Double.NaN;
    private double mLastTime = Double.NaN;
    private final long mDomainInterval = 5 * 60000;  // 5 min
    private final RunningMax mRunningMax = new RunningMax(250);

    private double mStartRrTime = Double.NEGATIVE_INFINITY;
    private double mLastRrTime;
    private double mLastUpdateTime;
    private double mTotalRrTime;

    private static final double RR_SCALE = .1;  // to 100 ms to use same axis
    SimpleDateFormat mDateFormat = new SimpleDateFormat(
            "HH:mm", Locale.US);
    SimpleDateFormat mDateFormatSec = new SimpleDateFormat(
            "HH:mm:ss", Locale.US);

    private XYSeriesFormatter<XYRegionFormatter> hrFormatter;
    private XYSeriesFormatter<XYRegionFormatter> rrFormatter;
    private SimpleXYSeries hrSeries;
    private SimpleXYSeries rrSeries;

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
        hrFormatter = new LineAndPointFormatter(Color.RED,
                null, null, null);
        hrFormatter.setLegendIconEnabled(false);
        rrFormatter = new LineAndPointFormatter(Color.rgb(0, 153, 255),
                null, null, null);
        rrFormatter.setLegendIconEnabled(false);

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
            private final SimpleDateFormat dateFormat = mDateFormat;

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

        if (mPlotHr) {
            hrSeries = new SimpleXYSeries("HR");
            mPlot.addSeries(hrSeries, hrFormatter);
        } else {
            hrSeries = null;
        }
        if (mPlotRr) {
            rrSeries = new SimpleXYSeries("RR");
            mPlot.addSeries(rrSeries, rrFormatter);
        } else {
            rrSeries = null;
        }

        // Pan and Zoom
        PanZoom.attach(mPlot, PanZoom.Pan.BOTH, PanZoom.Zoom.STRETCH_BOTH);
    }

//    public void addValues(long time, double hr, double rr) {
//        Log.d(TAG, this.getClass().getSimpleName() + ": addValues: time+"
//                + time + " hr=" + hr
//                + " rr=" + String.format(Locale.US, "%.1f", RR_SCALE * rr)
//                + " hrSize=" + hrSeries.size() + " rrSize=" + hrSeries.size
//                ());
//        if (mPlotHr) {
//            hrSeries.addLast(time, hr);
//        }
//        if (mPlotRr) {
//            rrSeries.addLast(time, RR_SCALE * rr);
//        }
//        update();
//    }

    public void addHrValue(double time, double hr) {
//        Log.d(TAG, this.getClass().getSimpleName() + ": addHrValues: time="
//                + mDateFormatSec.format(time) + " hr=" + hr + " hrSize=" + hrSeries.size());
        if (!mPlotHr) return;
        if (Double.isNaN(mPlotStartTime)) mPlotStartTime = time;
        if (Double.isNaN(mLastTime)) {
            mLastTime = time;
        } else if (time > mLastTime) {
            mLastTime = time;
        }
        mRunningMax.add(hr);
        hrSeries.addLast(time, hr);
    }

    public void addRrValues(double time, List<Integer> rrsMs) {
//        Log.d(TAG, this.getClass().getSimpleName() + ": addRrValues: time"
//                + mDateFormatSec.format(time) + " rrsMs.size=" + rrsMs.size()
//                + " rrSize=" + rrSeries.size());
        if (!mPlotRr || rrsMs.size() == 0) return;
        if (Double.isNaN(mPlotStartTime)) mPlotStartTime = time;
        if (Double.isNaN(mLastTime)) {
            mLastTime = time;
        } else if (time > mLastTime) {
            mLastTime = time;
        }
        long now = new Date().getTime();
        int nRrVals = rrsMs.size();
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
            mStartRrTime = mLastRrTime = mLastUpdateTime = now - totalRR;
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
        if (nRrVals > 0 && tVals[0] < mLastUpdateTime) {
            double deltaT = mLastUpdateTime = tVals[0];
            t += deltaT;
            for (int i = 0; i < nRrVals; i++) {
                tVals[i] += deltaT;
            }
        }
        // Keep them from being in the future
        if (t > now) {
            double deltaT = t - now;
            for (int i = 0; i < nRrVals; i++) {
                tVals[i] -= deltaT;
            }
        }
        // Add to the series
        for (int i = 0; i < nRrVals; i++) {
            rr = RR_SCALE * rrVals[i];
            mRunningMax.add(rr);
            rrSeries.addLast(tVals[i], rr);
            mLastRrTime = tVals[i];
        }
        mLastUpdateTime = now;
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
        if (hrSeries != null) {
            if (hrSeries.getxVals() != null) {
                sb.append("hrSeries Size=").append(hrSeries.getxVals().size()).append(LF);
            }
        } else {
            sb.append("hrSeries=Null").append(LF);
        }
        if (rrSeries != null) {
            if (rrSeries.getxVals() != null) {
                sb.append("rrSeries Size=").append(rrSeries.getxVals().size()).append(LF);
            }
        } else {
            sb.append("rrSeries=Null").append(LF);
        }
        return sb.toString();
    }

    public String getRrInfo() {
        double elapsed = .001 * (mLastRrTime - mStartRrTime);
        double total = .001 * mTotalRrTime;
        double ratio = total / elapsed;
        return "Tot=" + String.format(Locale.US, "%.2f s", elapsed)
                + " RR=" + String.format(Locale.US, "%.2f s", total)
                + " (" + String.format(Locale.US, "%.2f", ratio) + ")";
    }

    public void update() {
        double max = mRunningMax.max();
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
        activity.runOnUiThread(() -> {
            mPlot.redraw();
//                Log.d(TAG, HRPlotter.this.getClass().getSimpleName()
//                        + ": update (done)");
        });
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
