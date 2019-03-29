package com.polar.polarsdkecghrdemo;

import android.content.Context;
import android.graphics.Color;

import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYSeriesFormatter;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import polar.com.sdk.api.model.PolarHrData;

public class DatePlotter {
    private static final int NVALS = 360;

    String title;
    private String TAG = "Polar_Plotter";
    private PlotterListener listener;
    private Context context;
    private XYSeriesFormatter hrFormatter;
    private XYSeriesFormatter rrFormatter;
    private SimpleXYSeries hrSeries;
    private SimpleXYSeries rrSeries;
    private Double[] xHrVals = new Double[NVALS];
    private Double[] yHrVals = new Double[NVALS];
    private Double[] xRrVals = new Double[NVALS];
    private Double[] yRrVals = new Double[NVALS];

    public DatePlotter(Context context, String title) {
        this.context = context;
        this.title = title;
        Date now = new Date();
        double endTime = now.getTime();
        double startTime = endTime - NVALS * 1000;
        double delta = (endTime - startTime) / (NVALS - 1);

        for (int i = 0; i < NVALS; i++) {
            xHrVals[i] = new Double(startTime + i * delta);
            yHrVals[i] = new Double(60);
            xRrVals[i] = new Double(startTime + i * delta);
            yRrVals[i] = new Double(100);
//            yHrVals[i] = null;
        }

        hrFormatter = new LineAndPointFormatter(Color.RED,
                null, null, null);
        hrFormatter.setLegendIconEnabled(false);
        hrSeries = new SimpleXYSeries(Arrays.asList(xHrVals),
                Arrays.asList(yHrVals),
                "HR");

        rrFormatter = new LineAndPointFormatter(Color.BLUE,
                null, null, null);
        rrFormatter.setLegendIconEnabled(false);
        rrSeries = new SimpleXYSeries(Arrays.asList(xRrVals),
                Arrays.asList(yRrVals),
                "HR");
    }

    public SimpleXYSeries getHrSeries() {
        return (SimpleXYSeries) hrSeries;
    }

    public SimpleXYSeries getRrSeries() {
        return (SimpleXYSeries) rrSeries;
    }

    public XYSeriesFormatter getHrFormatter() {
        return hrFormatter;
    }

    public XYSeriesFormatter getRrFormatter() {
        return rrFormatter;
    }

    public void addValue(PolarHrData polarHrData) {
        Date now = new Date();
        long time = now.getTime();
        for (int i = 0; i < NVALS - 1; i++) {
            xHrVals[i] = xHrVals[i + 1];
            yHrVals[i] = yHrVals[i + 1];
            hrSeries.setXY(xHrVals[i], yHrVals[i], i);
        }
        xHrVals[NVALS - 1] = new Double(time);
        yHrVals[NVALS - 1] = new Double(polarHrData.hr);
        hrSeries.setXY(xHrVals[NVALS - 1], yHrVals[NVALS - 1], NVALS - 1);

        // Do RR
        // Assume the R points are time - intervalN - intervalN-1 ... - interval0
        // Gives approximately the right shape of the RR curve
        // But displaced by an unknown amount (since time is not in the data)
        // And the displacement is different for each reading
        List<Integer> rrsMs = polarHrData.rrsMs;
        int nRrVals = rrsMs.size();
        if (nRrVals > 0) {
            for (int i = 0; i < NVALS - nRrVals; i++) {
                xRrVals[i] = xRrVals[i + 1];
                yRrVals[i] = yRrVals[i + 1];
                rrSeries.setXY(xRrVals[i], yRrVals[i], i);
            }
            double totalRR = 0;
            double scale = .1;
            for (int i = 0; i < nRrVals; i++) {
                totalRR += scale * rrsMs.get(i);
            }
            int index = 0;
            double rr;
            for (int i = NVALS - nRrVals; i < NVALS; i++) {
                rr = scale * rrsMs.get(index++);
                xRrVals[i] = new Double(time - totalRR);
                yRrVals[i] = new Double(rr);
                totalRR -= rr;
                rrSeries.setXY(xRrVals[i], yRrVals[i], i);
            }
        }

        listener.update();
    }

//    public void sendSingleSample(float mV) {
//        plotNumbers[dataIndex] = mV;
//        if (dataIndex >= plotNumbers.length - 1) {
//            dataIndex = 0;
//        }
//        if (dataIndex < plotNumbers.length - 1) {
//            plotNumbers[dataIndex + 1] = null;
//        }
//
//        ((SimpleXYSeries) hrSeries).setModel(Arrays.asList(plotNumbers),
//                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);
//        dataIndex++;
//        listener.update();
//    }

    public void setListener(PlotterListener listener) {
        this.listener = listener;
    }
}
