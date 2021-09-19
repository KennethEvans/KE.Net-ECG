package net.kenevans.polar.polarecg;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYRegionFormatter;
import com.androidplot.xy.XYSeriesFormatter;
import com.polar.sdk.api.model.PolarEcgData;

@SuppressWarnings("WeakerAccess")
public class Plotter implements IConstants {

    private PlotterListener mListener;
    private final XYSeriesFormatter<XYRegionFormatter> mFormatter;
    private final SimpleXYSeries mSeries;
    /**
     * The next index in the data
     */
    private long mDataIndex;
    /**
     * The number of points to show
     */
    private final int mDataSize;
    /**
     * The total number of points to keep
     */
    private final int mTotalDataSize;


    public Plotter(int totalDataSize, int dataSize,
                   String title,
                   Integer lineColor, boolean showVertices) {
        this.mDataSize = dataSize;
        this.mTotalDataSize = totalDataSize;
        this.mDataIndex = 0;

        mFormatter = new LineAndPointFormatter(lineColor,
                showVertices ? lineColor : null, null, null);
        mFormatter.setLegendIconEnabled(false);

        mSeries = new SimpleXYSeries(title);
    }

    public SimpleXYSeries getmSeries() {
        return mSeries;
    }

    public XYSeriesFormatter<XYRegionFormatter> getmFormatter() {
        return mFormatter;
    }

    /**
     * Implements a strip chart adding new data at the end.
     *
     * @param plot         The associated XYPlot.
     * @param polarEcgData The data that came in.
     */
    public void addValues(XYPlot plot, PolarEcgData polarEcgData) {
//        Log.d(TAG,
//                "addValues: dataIndex=" + dataIndex + " seriesSize=" +
//                series.size());
        int nSamples = polarEcgData.samples.size();
        if (nSamples == 0) return;

        // Add the new values, removing old values if needed
        for (Integer val : polarEcgData.samples) {
            if (mSeries.size() >= mTotalDataSize) {
                mSeries.removeFirst();
            }
            // Convert from  μV to mV
            mSeries.addLast(mDataIndex++, .001 * val);
            updatePlot(plot);
//            Log.d(TAG, "addValues thread: " + Thread.currentThread()
//            .getName());
        }
    }

    public void updatePlot(XYPlot plot) {
        long plotMin, plotMax;
        plotMin = mDataIndex - mDataSize;
        plotMax = mDataIndex;
        plot.setDomainBoundaries(plotMin, plotMax, BoundaryMode.FIXED);
        mListener.update();
    }

    /**
     * Clear the plot and reset dataIndex;
     */
    public void clear() {
        mDataIndex = 0;
        mSeries.clear();
        mListener.update();
    }

    public void setmListener(PlotterListener mListener) {
        this.mListener = mListener;
    }

    public long getmDataIndex() {
        return mDataIndex;
    }
}
