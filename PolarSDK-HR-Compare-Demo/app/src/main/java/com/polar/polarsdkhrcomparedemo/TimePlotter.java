package com.polar.polarsdkhrcomparedemo;

import android.content.Context;
import android.util.Log;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeriesFormatter;

import java.util.Date;
import java.util.List;
import java.util.Locale;

import polar.com.sdk.api.model.PolarHrData;

/**
 * Implements two series for HR and RR using time for the x values.
 */
@SuppressWarnings("WeakerAccess")
public class TimePlotter {
    private String TAG = "Polar_Plotter";
    private String title;
    /**
     * Scale the RR values by RR_SCALE to use the same axis. (Could implement
     * NormedXYSeries and use two axes)
     */
    private double RR_SCALE = .1;
    private PlotterListener listener;
    private Context context;

    /**
     * The duration of the data to be retained.
     */
    private int duration;
    private XYSeriesFormatter hrFormatter;
    private XYSeriesFormatter rrFormatter;
    private SimpleXYSeries hrSeries;
    private SimpleXYSeries rrSeries;

    private double startRrTime = Double.NEGATIVE_INFINITY;
    private double lastRrTime;
    private double currentUpdateTime;
    private double lastUpdateTime;
    private double totalRrTime;

    public TimePlotter(Context context, int duration, String title,
                       Integer hrColor,
                       Integer rrColor) {
        this.context = context;
        this.duration = duration;
        this.title = title;  // Not used
        hrFormatter = new LineAndPointFormatter(hrColor, hrColor,
                null, null);
        hrFormatter.setLegendIconEnabled(false);
        hrSeries = new SimpleXYSeries("HR");

        rrFormatter = new LineAndPointFormatter(rrColor, rrColor,
                null, null);
        rrFormatter.setLegendIconEnabled(false);
        rrSeries = new SimpleXYSeries("RR");
    }

    public SimpleXYSeries getHrSeries() {
        return hrSeries;
    }

    public SimpleXYSeries getRrSeries() {
        return rrSeries;
    }

    public XYSeriesFormatter getHrFormatter() {
        return hrFormatter;
    }

    public XYSeriesFormatter getRrFormatter() {
        return rrFormatter;
    }

    /**
     * Implements a strip chart adding new data at the end.
     *
     * @param plot        The associated XYPlot.
     * @param polarHrData The HR data that came in.
     */
    public void addValues(XYPlot plot, PolarHrData polarHrData) {
        long now = new Date().getTime();
        currentUpdateTime = now;
        // Make the plot move forward
        long start = now - duration;
        plot.setDomainBoundaries(start, now, BoundaryMode.FIXED);
        // Clear out expired HR values
        if (hrSeries.size() > 0) {
            while (hrSeries.getX(0).longValue() < start) {
                hrSeries.removeFirst();
            }
            hrSeries.addLast(now, polarHrData.hr);
        }
        hrSeries.addLast(now, polarHrData.hr);

        // Do RR
        // We don't know at what time the RR intervals start.  All we know is
        // the time the data arrived (now) and that the intervals ended
        // between the previous update time and now.

        // Clear out expired RR values
        if (rrSeries.size() > 0) {
            while (rrSeries.getX(0).longValue() < start) {
                rrSeries.removeFirst();
            }
        }
        List<Integer> rrsMs = polarHrData.rrsMs;
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
        if (Double.isInfinite(startRrTime)) {
            startRrTime = lastRrTime = lastUpdateTime = now - totalRR;
            totalRrTime = 0;
        }
        totalRrTime += totalRR;
        Log.d(TAG, "lastRrTime=" + lastRrTime
                + " totalRR=" + totalRR
                + " elapsed=" + (lastRrTime - startRrTime)
                + " totalRrTime=" + totalRrTime);

        double rr;
        double t = lastRrTime;
        for (int i = 0; i < nRrVals; i++) {
            rr = rrVals[i];
            t += rr;
            tVals[i] = t;
        }
        // Keep them in this interval
        if (nRrVals > 0 && tVals[0] < lastUpdateTime) {
            double deltaT = lastUpdateTime = tVals[0];
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
            rr = rrVals[i];
            rrSeries.addLast(tVals[i], RR_SCALE * rr);
            lastRrTime = tVals[i];
        }
        lastUpdateTime = now;
        listener.update();
    }

    public String getRrInfo() {
        double elapsed = .001 * (lastRrTime - startRrTime);
        double total = .001 * totalRrTime;
        double ratio = total / elapsed;
        return "Tot=" + String.format(Locale.US, "%.2f s", elapsed)
                + " RR=" + String.format(Locale.US, "%.2f s", total)
                + " (" + String.format(Locale.US, "%.2f", ratio) + ")";
    }

    public void setListener(PlotterListener listener) {
        this.listener = listener;
    }
}
