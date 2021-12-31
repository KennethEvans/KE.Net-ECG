package net.kenevans.polar.polarecg;

import android.graphics.Color;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYRegionFormatter;
import com.androidplot.xy.XYSeriesFormatter;

@SuppressWarnings("WeakerAccess")
public class QRSPlotter implements IConstants {
    // The defaults are for 130 Hz
    private static final int mNLarge = 26;
    // The total number of points = nLarge * total large blocks desired
    private static final int mNTotalPoints = 150 * mNLarge;  // 150 = 30 sec
    private static final int mNPlotPoints = 20 * mNLarge;    // 20 points

    private final ECGActivity activity;
    private final XYPlot mPlot;

    private final XYSeriesFormatter<XYRegionFormatter> mFormatter;
    private final XYSeriesFormatter<XYRegionFormatter> mFormatter2;
    private final SimpleXYSeries mSeries;
    private final SimpleXYSeries mSeries2;
    /**
     * The next index in the data
     */
    private long mDataIndex;
    /**
     * The number of points to show
     */
    private final int mDataSize = mNPlotPoints;
    /**
     * The total number of points to keep
     */
    private final int mTotalDataSize = mNTotalPoints;


    public QRSPlotter(ECGActivity activity, XYPlot mPlot,
                      String title, boolean showVertices) {
        Log.d(TAG, this.getClass().getSimpleName() + " ECGPlotter CTOR");
        // This is the activity, needed for resources
        this.activity = activity;
        this.mPlot = mPlot;
        this.mDataIndex = 0;

        mFormatter = new LineAndPointFormatter(Color.RED,
                showVertices ? Color.RED : null, null, null);
        mFormatter.setLegendIconEnabled(false);
        mSeries = new SimpleXYSeries(title);
        mFormatter2 = new LineAndPointFormatter(Color.YELLOW,
                showVertices ? Color.YELLOW: null, null, null);
        mFormatter2.setLegendIconEnabled(false);
        mSeries2 = new SimpleXYSeries(title);
        setupPlot();
    }

    /**
     * Sets the plot parameters, calculating the range boundaries to have the
     * same grid as the domain.  Calls update when done.
     */
    public void setupPlot() {
        Log.d(TAG, this.getClass().getSimpleName() + " setupPlot");
        if(mPlot.getVisibility() == View.GONE) return;
//        DisplayMetrics displayMetrics = this.getResources()
//        .getDisplayMetrics();
//        float dpHeight = displayMetrics.heightPixels / displayMetrics.density;
//        float dpWidth = displayMetrics.widthPixels / displayMetrics.density;
//        Log.d(TAG, "dpWidth=" + dpWidth + " dpHeight=" + dpHeight);
//        Log.d(TAG, "widthPixels=" + displayMetrics.widthPixels +
//                " heightPixels=" + displayMetrics.heightPixels);
//        Log.d(TAG, "density=" + displayMetrics.density);
//        Log.d(TAG, "10dp=" + 10 / displayMetrics.density + " pixels");
//
//        Log.d(TAG, "plotWidth=" + mPlot.getWidth() +
//                " plotHeight=" + mPlot.getHeight());
//
//        RectF widgetRect = mPlot.getGraph().getWidgetDimensions().canvasRect;
//        Log.d(TAG,
//                "widgetRect LRTB=" + widgetRect.left + "," + widgetRect
//                .right +
//                        "," + widgetRect.top + "," + widgetRect.bottom);
//        Log.d(TAG, "widgetRect width=" + (widgetRect.right - widgetRect
//        .left) +
//                " height=" + (widgetRect.bottom - widgetRect.top));
//
//        RectF gridRect = mPlot.getGraph().getGridRect();
//        Log.d(TAG, "gridRect LRTB=" + gridRect.left + "," + gridRect.right +
//                "," + gridRect.top + "," + gridRect.bottom);
//        Log.d(TAG, "gridRect width=" + (gridRect.right - gridRect.left) +
//                " height=" + (gridRect.bottom - gridRect.top));

        // Calculate the range limits to make the blocks be square
        // Using .5 mV and nLarge / samplingRate for total grid size
        // rMax is half the total, rMax at top and -rMax at bottom
        RectF gridRect = mPlot.getGraph().getGridRect();
        double rMax =
                // Note different from ECG plot
                .125 * (gridRect.bottom - gridRect.top) * mDataSize /
                        mNLarge / (gridRect.right - gridRect.left);
        // Round it to one decimal point
        rMax = Math.round(rMax * 10) / 10.;
        Log.d(TAG, "    rMax = " + rMax);
        Log.d(TAG, "    gridRect LRTB=" + gridRect.left + "," + gridRect.right +
                "," + gridRect.top + "," + gridRect.bottom);
        Log.d(TAG, "    gridRect width=" + (gridRect.right - gridRect.left) +
                " height=" + (gridRect.bottom - gridRect.top));
        DisplayMetrics displayMetrics = activity.getResources()
                .getDisplayMetrics();
        Log.d(TAG, "    display widthPixels=" + displayMetrics.widthPixels +
                " heightPixels=" + displayMetrics.heightPixels);

        mPlot.addSeries(mSeries, mFormatter);
        mPlot.addSeries(mSeries2, mFormatter2);
        mPlot.setRangeBoundaries(-rMax, rMax, BoundaryMode.FIXED);
        // Set the range block to be .1 mV so a large block will be .5 mV
        mPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, .5);
//        mPlot.setLinesPerRangeLabel(5);
        mPlot.setDomainBoundaries(-mDataSize, 0, BoundaryMode.FIXED);
        // Set the domain block to be .2 * nlarge so large block will be
        // nLarge samples
        mPlot.setDomainStep(StepMode.INCREMENT_BY_VAL,
                mNLarge);
//        mPlot.setLinesPerDomainLabel(5);

        mPlot.getGraph().setLineLabelEdges(XYGraphWidget.Edge.NONE);

        // These don't work
//        mPlot.getTitle().position(0, HorizontalPositioning
//        .ABSOLUTE_FROM_RIGHT,
//                0,    VerticalPositioning.ABSOLUTE_FROM_TOP, Anchor
//                .RIGHT_TOP);
//        mPlot.getTitle().setAnchor(Anchor.BOTTOM_MIDDLE);
//        mPlot.getTitle().setMarginTop(200);
//        mPlot.getTitle().setPaddingTop(200);

//        mPlot.setRenderMode(Plot.RenderMode.USE_BACKGROUND_THREAD);

        // Update the plot
        update();
    }

    public SimpleXYSeries getSeries() {
        return mSeries;
    }

    /**
     * Implements a strip chart adding new data at the end.
     *
     * @param val1 Value for the first series.
     * @param val2 Value for the second series.
     */
    public void addValues(double val1, double val2) {
//        Log.d(TAG, this.getClass().getSimpleName()
//                + "addValues: dataIndex=" + mDataIndex + " mSeriesSize="
//                + mSeries.size() + " mSeries2Size=" + mSeries2.size()
//                + " val1=" + val1 + " val2=" + val2);

        if(mPlot.getVisibility() == View.GONE) return;
        // Add the new values, removing old values if needed
        if (mSeries.size() >= mTotalDataSize) {
            mSeries.removeFirst();
        }
        if (mSeries2.size() >= mTotalDataSize) {
            mSeries2.removeFirst();
        }
        // Convert from  μV to mV and add to series
        mSeries.addLast(mDataIndex, .001 * val1);
        mSeries2.addLast(mDataIndex, .001 * val2);
        mDataIndex++;
        // Reset the domain boundaries
        updateDomainBoundaries();
        update();
    }

    public void updateDomainBoundaries() {
        if(mPlot.getVisibility() == View.GONE) return;
        long plotMin, plotMax;
        plotMin = mDataIndex - mDataSize;
        plotMax = mDataIndex;
        mPlot.setDomainBoundaries(plotMin, plotMax, BoundaryMode.FIXED);
//        Log.d(TAG, this.getClass().getSimpleName() + " updateDomainBoundaries: "
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
        if(mPlot.getVisibility() == View.GONE) return;
        //            Log.d(TAG, this.getClass().getSimpleName()
        //                    + " update: thread: " + Thread.currentThread()
        //                    .getName());
        activity.runOnUiThread(mPlot::redraw);
    }

    /**
     * Clears the plot and resets dataIndex
     */
    public void clear() {
        if(mPlot.getVisibility() == View.GONE) return;
        mDataIndex = 0;
        mSeries.clear();
        update();
    }

    public long getDataIndex() {
        return mDataIndex;
    }
}
