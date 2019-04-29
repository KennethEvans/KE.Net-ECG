package net.kenevans.polar.polarecg;

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
    private Number[] plotNumbers = new Number[500];
    private XYSeriesFormatter formatter;
    private SimpleXYSeries series;
    /**
     * The next index in the data
     */
    private long dataIndex;
    /**
     * The number of points to show
     */
    private int dataSize;
    /**
     * The total number of points to keep
     */
    private int totalDataSize;


    public Plotter(int totalDataSize, int dataSize,
                   String title,
                   Integer lineColor, boolean showVertices) {
        this.title = title;
        this.dataSize = dataSize;
        this.totalDataSize = totalDataSize;
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
            if (series.size() >= totalDataSize) {
                series.removeFirst();
            }
            // Convert from  μV to mV
            series.addLast(dataIndex++, .001 * val);
            updatePlot(plot);
//            Log.d(TAG, "addValues thread: " + Thread.currentThread()
//            .getName());
        }
    }

    public void updatePlot(XYPlot plot) {
        long plotMin, plotMax;
        if (dataIndex < dataSize) {
            plotMin = dataIndex - dataSize;
            plotMax = dataIndex;
        } else {
            plotMin = dataIndex - dataSize;
            plotMax = dataIndex;
        }
        plot.setDomainBoundaries(plotMin, plotMax, BoundaryMode.FIXED);
        listener.update();
    }

    /**
     * Clear the plot and reset dataIndex;
     */
    public void clear() {
        dataIndex = 0;
        series.clear();
        listener.update();
    }

    public void setListener(PlotterListener listener) {
        this.listener = listener;
    }
}
