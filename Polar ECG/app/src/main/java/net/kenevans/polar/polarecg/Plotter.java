package net.kenevans.polar.polarecg;

import android.content.Context;
import android.util.Log;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeriesFormatter;

import polar.com.sdk.api.model.PolarEcgData;

@SuppressWarnings("WeakerAccess")
public class Plotter {

    String title;
    private String TAG = "Polar_Plotter";
    private PlotterListener listener;
    private Context context;
    private Number[] plotNumbers = new Number[500];
    private XYSeriesFormatter formatter;
    private SimpleXYSeries series;
    private long dataIndex;
    private int dataSize;


    public Plotter(Context context, int dataSize, String title,
                   Integer lineColor, boolean showVertices) {
        this.context = context;
        this.title = title;
        this.dataSize = dataSize;
        this.dataIndex = 0;

        formatter = new LineAndPointFormatter(lineColor,
                showVertices ? lineColor : null, null, null);
        formatter.setLegendIconEnabled(false);

        series = new SimpleXYSeries(title);
    }

    public SimpleXYSeries getSeries() {
        return series;
    }

    public XYSeriesFormatter getFormatter() {
        return formatter;
    }

    /**
     * Implements a strip chart adding new data at the end.
     *
     * @param plot         The associated XYPlot.
     * @param polarEcgData The data that came in.
     */
    public void addValues(XYPlot plot, PolarEcgData polarEcgData) {
        Log.d(TAG,
                "addValues: dataIndex=" + dataIndex + " seriesSize=" + series.size());
        int nSamples = polarEcgData.samples.size();
        if (nSamples == 0) return;

        // Add the new values, removing old values if needed
        for (Integer val : polarEcgData.samples) {
            if (series.size() >= dataSize) {
                series.removeFirst();
            }
            // Convert from  μV to mV
            series.addLast(dataIndex++, .001 * val);
        }
        updatePlot(plot);
    }

    private void updatePlot(XYPlot plot) {
        long plotMin, plotMax;
        if (dataIndex < dataSize) {
            plotMin = 0;
            plotMax = dataSize;
        } else {
            plotMin = dataIndex - dataSize;
            plotMax = dataIndex;
        }
        plot.setDomainBoundaries(plotMin, plotMax, BoundaryMode.FIXED);
        listener.update();
    }

    public void setListener(PlotterListener listener) {
        this.listener = listener;
    }
}
