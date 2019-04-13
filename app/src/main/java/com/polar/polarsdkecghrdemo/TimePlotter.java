package com.polar.polarsdkecghrdemo;

import android.content.Context;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeriesFormatter;

import java.util.Date;
import java.util.List;

import polar.com.sdk.api.model.PolarHrData;

/**
 * Implements two series for HR and RR using time for the x values.
 */
public class TimePlotter {
    private String title;
    private String TAG = "Polar_Plotter";
    private double RR_SCALE = .1;
    private PlotterListener listener;
    private Context context;
    private int duration;
    private XYSeriesFormatter hrFormatter;
    private XYSeriesFormatter rrFormatter;
    private SimpleXYSeries hrSeries;
    private SimpleXYSeries rrSeries;

    public TimePlotter(Context context, int duration, String title,
                       Integer hrColor,
                       Integer rrColor) {
        this.context = context;
        this.duration = duration;
        this.title = title;  // Not used
        Date now = new Date();
        hrFormatter = new LineAndPointFormatter(hrColor, null,
                null, null);
        hrFormatter.setLegendIconEnabled(false);
        hrSeries = new SimpleXYSeries("HR");

        rrFormatter = new LineAndPointFormatter(rrColor, null,
                null, null);
        rrFormatter.setLegendIconEnabled(false);
        rrSeries = new SimpleXYSeries("HR");
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
     * @param polarHrData The HR data that came in.
     */
    public void addValues(XYPlot plot, PolarHrData polarHrData) {
        Date date = new Date();
        long now = date.getTime();
        long start = now - duration;
        plot.setDomainBoundaries(start, now, BoundaryMode.FIXED);
        // Clear out expired values
        if (hrSeries.size() > 0) {
            while (hrSeries.getX(0).longValue() < start) {
                hrSeries.removeFirst();
            }
            hrSeries.addLast(now, polarHrData.hr);
        }
        hrSeries.addLast(now, polarHrData.hr);

        // Do RR
        // We don't know at what now the RR intervals start.  All we know is
        // the now the data arrived (the current now, date). This
        // implementation assumes they end at the current now, and spaces them
        // out in the past accordingly.  This seems to get the
        // relative positioning reasonably well.

        // Clear out expired values
        if (rrSeries.size() > 0) {
            while (rrSeries.getX(0).longValue() < start) {
                rrSeries.removeFirst();
            }
        }
        // Scale the RR values by this to use the same axis. (Could implement
        // NormedXYSeries and use two axes)
        List<Integer> rrsMs = polarHrData.rrsMs;
        int nRrVals = rrsMs.size();
        if (nRrVals > 0) {
            double totalRR = 0;
            for (int i = 0; i < nRrVals; i++) {
                totalRR += RR_SCALE * rrsMs.get(i);
            }
            int index = 0;
            double rr;
            for (int i = nRrVals - 1; i >= 0; i--) {
                rr = RR_SCALE * rrsMs.get(index++);
                totalRR -= rr;
                rrSeries.addLast(now - totalRR, rr);
            }
        }
        listener.update();
    }

    public void setListener(PlotterListener listener) {
        this.listener = listener;
    }
}
