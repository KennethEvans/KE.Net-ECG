package net.kenevans.polar.polarecg;

import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.util.Log;

import com.androidplot.Plot;
import com.androidplot.util.DisplayDimensions;
import com.androidplot.util.PixelUtils;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYRegionFormatter;
import com.androidplot.xy.XYSeriesFormatter;
import com.polar.sdk.api.model.PolarEcgData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@SuppressWarnings("WeakerAccess")
public class ECGPlotter implements IConstants, IQRSConstants {
    private final ECGActivity activity;
    private XYPlot mPlot;

    private final XYSeriesFormatter<XYRegionFormatter> mFormatter;
    private final SimpleXYSeries mSeries;
    /**
     * The next index in the data
     */
    private long mDataIndex;

    public static final SimpleDateFormat sdfshort =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);


    public ECGPlotter(ECGActivity activity, XYPlot plot,
                      String title, Integer lineColor, boolean showVertices) {
        Log.d(TAG, this.getClass().getSimpleName() + " ECGPlotter CTOR");
        // This is the activity, needed for resources
        this.activity = activity;
        this.mPlot = plot;
        this.mDataIndex = 0;

        mFormatter = new LineAndPointFormatter(lineColor,
                showVertices ? lineColor : null, null, null);
        mFormatter.setLegendIconEnabled(false);
        mSeries = new SimpleXYSeries(title);
        mPlot.addSeries(mSeries, mFormatter);
        setupPlot();
    }

    /**
     * Sets the plot parameters, calculating the range boundaries to have the
     * same grid as the domain.  Calls update when done.
     */
    public void setupPlot() {
        Log.d(TAG, this.getClass().getSimpleName() + " setupPlot");

//        mPlot.getGraph().refreshLayout();
        mPlot.layout(mPlot.getDisplayDimensions());

        // Calculate the range limits to make the blocks be square.
        // A large box is .5 mV. rMax corresponds to half the total
        // number of large boxes, rMax at top and -rMax at bottom
        RectF gridRect = mPlot.getGraph().getGridRect();
        double rMax = .25 * N_DOMAIN_LARGE_BOXES * gridRect.height()
                / gridRect.width();

//        mPlot.getGraph().setLineLabelEdges(XYGraphWidget.Edge.NONE);

//        // DEBUG
//        mPlot.getGraph().setLineLabelEdges(XYGraphWidget.Edge.LEFT);
//        mPlot.getGraph().getLineLabelInsets().setLeft(PixelUtils.dpToPix(0));
//        mPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).getPaint()
//                .setColor(Color.RED);
//        mPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).getPaint()
//                .setTextSize(PixelUtils.dpToPix(10f));

        // Range
        mPlot.setRangeBoundaries(-rMax, rMax, BoundaryMode.FIXED);
        // Set the range block to be .1 mV so a large block will be .5 mV
        mPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, .1);
        mPlot.setLinesPerRangeLabel(5);
        // Make it be centered
        mPlot.setUserRangeOrigin(0.);
        // Make the x axis visible
        int color = mPlot.getGraph().getRangeGridLinePaint().getColor();
        mPlot.getGraph().getRangeOriginLinePaint().setColor(color);
        mPlot.getGraph().getRangeOriginLinePaint().setStrokeWidth(
                PixelUtils.dpToPix(1.5f));

        // Domain
        mPlot.setDomainBoundaries(-N_ECG_PLOT_POINTS, 0, BoundaryMode.FIXED);
        // Set the domain block to be .2 * nlarge so large block will be
        // nLarge samples
        mPlot.setDomainStep(StepMode.INCREMENT_BY_VAL,
                .2 * N_LARGE);
        mPlot.setLinesPerDomainLabel(5);

        mPlot.calculateMinMaxVals();

//        mPlot.invalidate();

//        // Update the plot
//        update();

        Log.d(TAG, "    renderMode=" + (mPlot.getRenderMode()
                == Plot.RenderMode.USE_MAIN_THREAD ? "Main" : "Background"));
        DisplayDimensions dims = mPlot.getDisplayDimensions();
        Log.d(TAG, "    view LRTB=" + mPlot.getLeft()
                + "," + mPlot.getRight() + "," + mPlot.getTop()
                + "," + mPlot.getBottom());
        Log.d(TAG, "    canvasRect LRTB=" + dims.canvasRect.left
                + "," + dims.canvasRect.right + "," + dims.canvasRect.top
                + "," + dims.canvasRect.bottom);
        Log.d(TAG, "    marginatedRect LRTB=" + dims.marginatedRect.left
                + "," + dims.marginatedRect.right + "," + dims.marginatedRect.top
                + "," + dims.marginatedRect.bottom);
        Log.d(TAG, "    paddedRect LRTB=" + dims.paddedRect.left
                + "," + dims.paddedRect.right + "," + dims.paddedRect.top
                + "," + dims.paddedRect.bottom);
        Log.d(TAG, "    gridRect LRTB=" + gridRect.left + "," + gridRect.right +
                "," + gridRect.top + "," + gridRect.bottom);
        Log.d(TAG, "    gridRect width=" + gridRect.width() +
                " height=" + gridRect.height());
        DisplayMetrics displayMetrics = activity.getResources()
                .getDisplayMetrics();
        Log.d(TAG, "    display widthPixels=" + displayMetrics.widthPixels +
                " heightPixels=" + displayMetrics.heightPixels);
        Log.d(TAG, "    rMax = " + rMax);
        Log.d(TAG, String.format(
                "    Range: min=%.3f step=%.3f max=%.3f origin=%.3f",
                mPlot.getBounds().getMinY().doubleValue(),
                mPlot.getRangeStepValue(),
                mPlot.getBounds().getMaxY().doubleValue(),
                mPlot.getRangeOrigin().doubleValue()));
        Log.d(TAG,
                "    innerLimits min,max=" + mPlot.getInnerLimits().getMinY()
                        + "," + mPlot.getInnerLimits().getMinY());
        Log.d(TAG,
                "    outerLimits min,max=" + mPlot.getOuterLimits().getMinY()
                        + "," + mPlot.getOuterLimits().getMinY());
        double screenYMax = mPlot.seriesToScreenY(rMax);
        double screenYMin = mPlot.seriesToScreenY(-rMax);
        double screenTop = mPlot.seriesToScreenY(mPlot.getBounds().getMaxY());
        double screenBottom = mPlot.seriesToScreenY(mPlot.getBounds().getMinY());
        Log.d(TAG, "    screenY(rMax)=" + screenYMax
                + " screenY(-rMax)=" + screenYMin
                + " screenY(top)=" + screenTop
                + " screenY(bottom)=" + screenBottom);
        Log.d(TAG, "    layoutRequested=" + mPlot.isLayoutRequested()
                + " isLaidOut=" + mPlot.isLaidOut()
                + " isInLayout=" + mPlot.isInLayout());
        Log.d(TAG, "    Time=" + sdfshort.format(new Date())
                + " mOrientationChangedECG=" + activity.mOrientationChangedECG
                + " height=" + mPlot.getHeight()
                + " isLaidOut=" + mPlot.isLaidOut());
    }

    public SimpleXYSeries getSeries() {
        return mSeries;
    }

    /**
     * Implements a strip chart adding new data at the end.
     *
     * @param polarEcgData The data that came in.
     */
    public void addValues(PolarEcgData polarEcgData) {
//        Log.d(TAG,
//                "addValues: dataIndex=" + dataIndex + " seriesSize=" +
//                series.size());
        int nSamples = polarEcgData.samples.size();
        if (nSamples == 0) return;

        // Add the new values, removing old values if needed
        for (Integer val : polarEcgData.samples) {
            if (mSeries.size() >= N_TOTAL_POINTS) {
                mSeries.removeFirst();
            }
//            // DEBUG Generate test values +- 1 mV
//            double testVal = 0.0;
//            if ((mDataIndex % 25) == 0) testVal = -1.0;
//            if ((mDataIndex % 50) == 0) testVal = 1.0;
//            mSeries.addLast(mDataIndex++, testVal);

            // Convert from  μV to mV and add to series
            mSeries.addLast(mDataIndex++, MICRO_TO_MILLI_VOLT * val);
        }
        // Reset the domain boundaries
        updateDomainBoundaries();
        update();
    }

    public void updateDomainBoundaries() {
        long plotMin, plotMax;
        plotMin = mDataIndex - N_ECG_PLOT_POINTS;
        plotMax = mDataIndex;
        mPlot.setDomainBoundaries(plotMin, plotMax, BoundaryMode.FIXED);
//        Log.d(TAG, this.getClass().getSimpleName() + " updatePlot: "
//                + "plotMin=" + plotMin + " plotmax=" + plotMax
//                + " size=" + mSeries.size());
//        int colorInt = mPlot.getGraph().getGridBackgroundPaint().getColor();
//        String hexColor = String.format("#%06X", (0xFFFFFF & colorInt));
//        Log.d(TAG, "gridBgColor=" + hexColor);
    }

    /**
     * Updates the plot. Runs on the UI thread.
     */
    public void update() {
        //            Log.d(TAG, this.getClass().getSimpleName()
        //                    + " update: thread: " + Thread.currentThread()
        //                    .getName());
        Log.d(TAG, "ECGPLot update: " + sdfshort.format(new Date())
            + " mOrientationChangedECG=" + activity.mOrientationChangedECG
            + " height=" + mPlot.getHeight()
            + " isLaidOut=" + mPlot.isLaidOut());
        activity.runOnUiThread(mPlot::redraw);
    }

    /**
     * Clears the plot and resets dataIndex.
     */
    public void clear() {
        mDataIndex = 0;
        mSeries.clear();
        update();
    }

    public void resetPlotInstance(XYPlot plot) {
        mPlot = plot;
        mPlot.clear();
        mPlot.addSeries(mSeries, mFormatter);
    }

    public long getDataIndex() {
        return mDataIndex;
    }
}
