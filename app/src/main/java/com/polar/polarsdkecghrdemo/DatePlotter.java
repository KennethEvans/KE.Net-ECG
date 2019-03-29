package com.polar.polarsdkecghrdemo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;

import com.androidplot.ui.Formatter;
import com.androidplot.xy.AdvancedLineAndPointRenderer;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYSeriesFormatter;

import java.util.Arrays;
import java.util.Date;

import polar.com.sdk.api.model.PolarHrData;

public class DatePlotter {
    private static final int NVALS = 360;

    String title;
    private String TAG = "Polar_Plotter";
    private PlotterListener listener;
    private Context context;
    private XYSeriesFormatter formatter;
    private SimpleXYSeries series;
    private Double[] xVals = new Double[NVALS];
    private Double[] yVals = new Double[NVALS];

    public DatePlotter(Context context, String title) {
        this.context = context;
        this.title = title;
        Date now = new Date();
        double endTime = now.getTime();
        double startTime = endTime - NVALS * 1000;
        double delta = (endTime - startTime) / (NVALS - 1);

        for (int i = 0; i < NVALS; i++) {
            xVals[i] = new Double(startTime + i * delta);
            yVals[i] = new Double(60);
//            yVals[i] = null;
        }

        formatter = new LineAndPointFormatter(Color.RED,
                null, null, null);
        formatter.setLegendIconEnabled(false);

        series = new SimpleXYSeries(Arrays.asList(xVals), Arrays.asList(yVals),
                "HR");
    }

    public SimpleXYSeries getSeries() {
        return (SimpleXYSeries) series;
    }

    public XYSeriesFormatter getFormatter() {
        return formatter;
    }

    public void addValue(PolarHrData polarHrData) {
        Date now = new Date();
        long time = now.getTime();
        for (int i = 0; i < NVALS -1; i++) {
            xVals[i] = xVals[i+1];
            yVals[i] = yVals[i+1];
            series.setXY(xVals[i], yVals[i], i);
        }
        xVals[NVALS -1] = new Double(time);
        yVals[NVALS-1] = new Double(polarHrData.hr);
        series.setXY(xVals[NVALS -1], yVals[NVALS -1], NVALS -1);
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
//        ((SimpleXYSeries) series).setModel(Arrays.asList(plotNumbers),
//                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);
//        dataIndex++;
//        listener.update();
//    }

    public void setListener(PlotterListener listener) {
        this.listener = listener;
    }
}
