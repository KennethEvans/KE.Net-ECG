package net.kenevans.polar.polarecg;

import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.util.Log;

import com.androidplot.Plot;
import com.androidplot.ui.Insets;
import com.androidplot.util.DisplayDimensions;
import com.androidplot.util.PixelUtils;
import com.androidplot.util.RectFUtils;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PanZoom;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYRegionFormatter;
import com.androidplot.xy.XYSeriesFormatter;
import com.polar.sdk.api.model.PolarEcgData;

import java.util.Date;
import java.util.Locale;

@SuppressWarnings("WeakerAccess")
public class ECGPlotter implements IConstants, IQRSConstants {
    private ECGActivity mActivity;
    private XYPlot mPlot;

    private XYSeriesFormatter<XYRegionFormatter> mFormatter;
    private SimpleXYSeries mSeries;

    /**
     * The next index in the data (or the length of the series.)
     */
    public long mDataIndex;

    /**
     * CTOR that just sets the plot.
     *
     * @param plot The XYPlot.
     */
    public ECGPlotter(XYPlot plot) {
        this.mPlot = plot;
        // Don't do anything else
    }

    public ECGPlotter(ECGActivity activity, XYPlot plot,
                      String title, Integer lineColor, boolean showVertices) {
        Log.d(TAG, this.getClass().getSimpleName() + " ECGPlotter CTOR");
        // This is the mActivity, needed for resources
        this.mActivity = activity;
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
     * Get a new ECGPLotter instance, using the given XYPlot but other values
     * from the current one. Use for replacing the current plotter.
     *
     * @param plot The XYPLot.
     * @return The new instance.
     */
    public ECGPlotter getNewInstance(XYPlot plot) {
//        Log.d(TAG, this.getClass().getSimpleName() + " getNewInstance: "
//                + " plot=" + Utils.getHashCode(plot)
//                + " mPlot=" + Utils.getHashCode(mPlot));

        ECGPlotter newPlotter = new ECGPlotter(plot);
        newPlotter.mPlot = plot;
        newPlotter.mActivity = this.mActivity;
        newPlotter.mDataIndex = this.mDataIndex;
        newPlotter.mFormatter = this.mFormatter;
        newPlotter.mSeries = this.mSeries;
        newPlotter.mPlot.addSeries(mSeries, mFormatter);
        newPlotter.setupPlot();
        return newPlotter;
    }

    /**
     * Sets the plot parameters, calculating the range boundaries to have the
     * same grid as the domain. Calls update when done.
     */
    public void setupPlot() {
        Log.d(TAG, this.getClass().getSimpleName() + " setupPlot");
//        Log.d(TAG, "    thread: " + Thread.currentThread().getName()
//                + " writeHoldCount=" + mLock.getWriteHoldCount()
//                + " readHoldCount=" + mLock.getReadHoldCount()
//                + " isWriteLockedByCurrentThread="
//                + mLock.isWriteLockedByCurrentThread()
//        );
        try {
            // Calculate the range limits to make the blocks be square.
            // A large box is .5 mV. rMax corresponds to half the total
            // number of large boxes, rMax at top and -rMax at bottom
            double rMax;
            RectF gridRect = mPlot.getGraph().getGridRect();
            if (gridRect == null) {
                Log.d(TAG, "ECGPlotter.setupPLot: gridRect is null\n"
                        + "    thread: " + Thread.currentThread().getName()
                );
                return;
            } else {
                rMax = .25 * N_DOMAIN_LARGE_BOXES * gridRect.height()
                        / gridRect.width();
            }

//        // DEBUG
//        mPlot.getGraph().setLineLabelEdges(XYGraphWidget.Edge.LEFT);
//        mPlot.getGraph().getLineLabelInsets().setLeft(PixelUtils.dpToPix(0));
//        mPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).getPaint()
//                .setColor(Color.RED);
//        mPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).getPaint()
//                .setTextSize(PixelUtils.dpToPix(10f));

            // Range
            // Set the range block to be .1 mV so a large block will be .5 mV
            mPlot.setRangeBoundaries(-rMax, rMax, BoundaryMode.FIXED);
            // Make the x axis visible
            int color = mPlot.getGraph().getRangeGridLinePaint().getColor();
            mPlot.getGraph().getRangeOriginLinePaint().setColor(color);
            mPlot.getGraph().getRangeOriginLinePaint().setStrokeWidth(
                    PixelUtils.dpToPix(1.5f));
            mPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, .1);
            mPlot.setLinesPerRangeLabel(5);
            // Make it be centered
            mPlot.setUserRangeOrigin(0.);

            // Domain
            updateDomainBoundaries();
            // Set the domain block to be .2 * nlarge so large block will be
            // nLarge samples
            mPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, .2 * N_LARGE);
            mPlot.setLinesPerDomainLabel(5);

//        // Allow panning
//        PanZoom.attach(mPlot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom.NONE);

//            // DEBUG
//            Log.d(TAG, this.getClass().getSimpleName()
//                    + " setupPlot: Before update\n" + getLogInfo(rMax));

            // Update the plot
            update();
        } catch (Exception ex) {
            String msg = "Error in ECGPLotter.setupPLot:\n"
                    + "isLaidOut=" + mPlot.isLaidOut()
                    + " width=" + mPlot.getWidth()
                    + " height=" + mPlot.getHeight();
            Utils.excMsg(mActivity, msg, ex);
            Log.e(TAG, msg, ex);
        }
    }

    @SuppressWarnings("unused")
    public String getLogInfo(double rMax) {
        RectF gridRect = mPlot.getGraph().getGridRect();
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("    orientation=")
                    .append(Utils.getOrientation(mActivity)).append("\n");
            sb.append("    renderMode=").append(mPlot.getRenderMode()
                            == Plot.RenderMode.USE_MAIN_THREAD ? "Main" :
                            "Background")
                    .append("\n");
            Insets gridInsets = mPlot.getGraph().getGridInsets();
            Insets lineLabelInsets = mPlot.getGraph().getLineLabelInsets();
            sb.append("    view plotMargins LRTB=")
                    .append(mPlot.getPlotMarginLeft()).append(",")
                    .append(mPlot.getPlotMarginRight()).append(",")
                    .append(mPlot.getPlotMarginTop()).append(",").
                    append(mPlot.getPlotMarginBottom()).append("\n");
            sb.append("    view plotPadding LRTB=")
                    .append(mPlot.getPlotPaddingLeft()).append(",")
                    .append(mPlot.getPlotPaddingRight()).append(",")
                    .append(mPlot.getPlotPaddingTop()).append(",").
                    append(mPlot.getPlotPaddingBottom()).append("\n");
            sb.append("    gridInsets LRTB=")
                    .append(gridInsets.getLeft()).append(",")
                    .append(gridInsets.getRight()).append(",")
                    .append(gridInsets.getTop()).append(",").
                    append(gridInsets.getBottom()).append("\n");
            sb.append("    lineLabelInsets LRTB=")
                    .append(lineLabelInsets.getLeft()).append(",")
                    .append(lineLabelInsets.getRight()).append(",")
                    .append(lineLabelInsets.getTop()).append(",")
                    .append(lineLabelInsets.getBottom()).append("\n");
            DisplayDimensions dims = mPlot.getDisplayDimensions();
            XYGraphWidget graph = mPlot.getGraph();
            RectF calcRect = RectFUtils.applyInsets(dims.paddedRect,
                    graph.getGridInsets());
            calcRect = RectFUtils.applyInsets(calcRect,
                    graph.getLineLabelInsets());
            sb.append("    view LRTB=").append(mPlot.getLeft()).append(",").
                    append(mPlot.getRight()).append(",")
                    .append(mPlot.getTop()
                    ).append(",").append(mPlot.getBottom()).append("\n");
            sb.append("    canvasRect LRTB=").append(dims.canvasRect.left)
                    .append(",").append(dims.canvasRect.right)
                    .append(",").append(dims.canvasRect.top)
                    .append(",").append(dims.canvasRect.bottom)
                    .append("\n");
            sb.append("    marginatedRect LRTB=")
                    .append(dims.marginatedRect.left)
                    .append(",").append(dims.marginatedRect.right).append(",")
                    .append(dims.marginatedRect.top).append(",")
                    .append(dims.marginatedRect.bottom).append("\n");
            sb.append("    paddedRect LRTB=")
                    .append(dims.paddedRect.left).append(",")
                    .append(dims.paddedRect.right).append(",")
                    .append(dims.paddedRect.top).append(",").
                    append(dims.paddedRect.bottom).append("\n");
            sb.append("    calcRect LRTB=")
                    .append(calcRect.left).append(",")
                    .append(calcRect.right).append(",")
                    .append(calcRect.top).append(",")
                    .append(calcRect.bottom).append("\n");
            if (gridRect == null) {
                sb.append("    gridRect LRTB=Unknown").append("\n");
            } else {
                sb.append("    gridRect LRTB=").append(gridRect.left)
                        .append(",").append(gridRect.right).append(",")
                        .append(gridRect.top).append(",")
                        .append(gridRect.bottom)
                        .append("\n");
            }
            sb.append("    view width=").append(mPlot.getWidth())
                    .append(" height=").append(mPlot.getHeight())
                    .append("\n");
            sb.append("    canvasRect width=").append(dims.canvasRect.width())
                    .append(" height=").append(dims.canvasRect.height())
                    .append("\n");
            sb.append("    marginatedRect width=")
                    .append(dims.marginatedRect.width())
                    .append(" height=").append(dims.marginatedRect.height())
                    .append("\n");
            sb.append("    paddedRect width=").append(dims.paddedRect.width())
                    .append(" height=").append(dims.paddedRect.height())
                    .append("\n");
            sb.append("    calcRect width=").append(calcRect.width())
                    .append(" height=").append(calcRect.height())
                    .append("\n");
            if (gridRect == null) {
                sb.append("    gridRect width=").append("Unknown")
                        .append(" height=").append("Unknown").append("\n");
            } else {
                sb.append("    gridRect width=").append(gridRect.width())
                        .append(" height=").append(gridRect.height())
                        .append("\n");
                sb.append("    gridRect: marginatedRect width=")
                        .append(graph.getMarginatedRect(gridRect).width())
                        .append(" height=")
                        .append(graph.getMarginatedRect(gridRect).height())
                        .append("\n");
                sb.append("    gridRect: paddedRect width=")
                        .append(graph.getPaddedRect(gridRect).width())
                        .append(" height=")
                        .append(graph.getPaddedRect(gridRect).height())
                        .append("\n");
            }
            DisplayMetrics displayMetrics = mActivity.getResources()
                    .getDisplayMetrics();
            sb.append("    display widthPixels=").
                    append(displayMetrics.widthPixels)
                    .append(" heightPixels=")
                    .append(displayMetrics.heightPixels).append("\n");
            sb.append("    rMax = ").append(rMax).append("\n");
            sb.append(String.format(Locale.US,
                    "    Range: min=%.3f step=%.3f max=%.3f origin=%.3f",
                    mPlot.getBounds().getMinY().doubleValue(),
                    mPlot.getRangeStepValue(),
                    mPlot.getBounds().getMaxY().doubleValue(),
                    mPlot.getRangeOrigin().doubleValue())).append("\n");
            sb.append(String.format(Locale.US,
                    "    Domain: min=%.3f step=%.3f max=%.3f origin=%.3f",
                    mPlot.getBounds().getMinX().doubleValue(),
                    mPlot.getDomainStepValue(),
                    mPlot.getBounds().getMaxX().doubleValue(),
                    mPlot.getDomainOrigin().doubleValue())).append("\n");
            sb.append("    innerLimits min,max=")
                    .append(mPlot.getInnerLimits().getMinY()).append(",")
                    .append(mPlot.getInnerLimits().getMinY()).append("\n");
            sb.append("    outerLimits min,max=")
                    .append(mPlot.getOuterLimits().getMinY()).append(",")
                    .append(mPlot.getOuterLimits().getMinY()).append("\n");
            double screenYMax = mPlot.seriesToScreenY(rMax);
            double screenYMin = mPlot.seriesToScreenY(-rMax);
            double screenTop =
                    mPlot.seriesToScreenY(mPlot.getBounds().getMaxY());
            double screenBottom =
                    mPlot.seriesToScreenY(mPlot.getBounds().getMinY());
            sb.append("    screenY(rMax)=").append(screenYMax)
                    .append(" " + "screenY")
                    .append("(-rMax)=").append(screenYMin)
                    .append(" screenY(top)=").append(screenTop)
                    .append(" screenY(bottom)=").append(screenBottom)
                    .append("\n");
//            sb.append("    layoutRequested=").append(mPlot
//            .isLayoutRequested())
//                    .append(" isLaidOut=").append(mPlot.isLaidOut())
//                    .append(" isInLayout=").append(mPlot.isInLayout())
//                    .append("\n");

            // ECG Specific
            sb.append("    mDataIndex=").append(mDataIndex).append(" mSeries:" +
                    " " +
                    "size=").append(mSeries.getxVals().size()).append(
                    "\n");
            sb.append("    time=").append(ECGActivity.sdfShort.format(new Date()))
                    .append(" mOrientationChangedECG=")
                    .append(mActivity.mOrientationChangedECG)
                    .append(" height=").append(mPlot.getHeight()).append(" " +
                            "isLaidOut=")
                    .append(mPlot.isLaidOut()).append("\n");
        } catch (Exception ex) {
            sb.append("    !!! Exception encountered in ECG getLogInfo:")
                    .append("\n        ").append(ex).append("\n        ")
                    .append(ex.getMessage());
            return sb.toString();
        }
        return sb.toString();
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
//            Log.d(TAG, this.getClass().getSimpleName() + " updatePlot: "
//                    + "plotMin=" + plotMin + " plotmax=" + plotMax
//                    + " size=" + mSeries.size());
//            int colorInt = mPlot.getGraph().getGridBackgroundPaint()
//            .getColor();
//            String hexColor = String.format("#%06X", (0xFFFFFF & colorInt));
//            Log.d(TAG, "gridBgColor=" + hexColor);
    }

    /**
     * Updates the plot. Runs on the UI thread.
     */
    public void update() {
//        Log.d(TAG, this.getClass().getSimpleName()
//                + " update: thread: " + Thread.currentThread().getName()
//                + " writeHoldCount=" + mLock.getWriteHoldCount()
//                + " readHoldCount=" + mLock.getReadHoldCount()
//                + " isWriteLockedByCurrentThread="
//                + mLock.isWriteLockedByCurrentThread()
//        );
//        Log.d(TAG, "ECGPLot update: " + ECGActivity.sdfShort.format(new
//        Date())
//                + " mOrientationChangedECG=" + mActivity
//                .mOrientationChangedECG
//                + " height=" + mPlot.getHeight()
//                + " isLaidOut=" + mPlot.isLaidOut()
//                + " plotter=" + Utils.getHashCode(this)
//                + " plot=" + Utils.getHashCode(mPlot)
//        );
        mActivity.runOnUiThread(mPlot::redraw);
    }

    /**
     * Set panning on or off.
     *
     * @param on Whether to be on or off (true for on).
     */
    public void setPanning(boolean on) {
        if (on) {
            PanZoom.attach(mPlot, PanZoom.Pan.HORIZONTAL,
                    PanZoom.Zoom.NONE);
        } else {
            PanZoom.attach(mPlot, PanZoom.Pan.NONE, PanZoom.Zoom.NONE);
        }
    }

    /**
     * Gets info about the view.
     */
    @SuppressWarnings("unused")
    private String getPlotInfo() {
        final String LF = "\n";
        StringBuilder sb = new StringBuilder();

        if (mPlot == null) {
            sb.append("Plot is null");
            return sb.toString();
        }
        sb.append("Title=").append(mPlot.getTitle().getText()).append(LF);
        sb.append("Range Title=").append(mPlot.getRangeTitle().getText())
                .append(LF);
        sb.append("Domain Title=").append(mPlot.getDomainTitle().getText())
                .append(LF);
        sb.append("Range Origin=").append(mPlot.getRangeOrigin()).append(LF);
        long timeVal = mPlot.getDomainOrigin().longValue();
        Date date = new Date(timeVal);
        sb.append("Domain Origin=").append(date).append(LF);
        sb.append("Range Step Value=").append(mPlot.getRangeStepValue())
                .append(LF);
        sb.append("Domain Step Value=").append(mPlot.getDomainStepValue())
                .append(LF);
        sb.append("Graph Width=").append(mPlot.getGraph().getSize()
                .getWidth().getValue()).append(LF);
        sb.append("Graph Height=").append(mPlot.getGraph().getSize()
                .getHeight().getValue()).append(LF);
        sb.append("mDataIndex=").append(mDataIndex).append(LF);
        if (mSeries != null) {
            if (mSeries.getxVals() != null) {
                sb.append("mSeries Size=")
                        .append(mSeries.getxVals().size()).append(LF);
            }
        } else {
            sb.append("mSeries=Null").append(LF);
        }
        return sb.toString();
    }

    /**
     * Clears the plot and resets dataIndex.
     */
    public void clear() {
        mDataIndex = 0;
        mSeries.clear();
        update();
    }

    public long getDataIndex() {
        return mDataIndex;
    }
}
