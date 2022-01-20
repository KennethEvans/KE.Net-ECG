package net.kenevans.polar.polarecg;

import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.util.Log;

import com.androidplot.Plot;
import com.androidplot.util.DisplayDimensions;
import com.androidplot.util.PixelUtils;
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
     * The next index in the data
     */
    private long mDataIndex;


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
        Log.d(TAG, this.getClass().getSimpleName() + " setupPlot"
                + " plotter=" + Utils.getHashCode(this)
                + " plot=" + Utils.getHashCode(mPlot)
        );

        // Calculate the range limits to make the blocks be square.
        // A large box is .5 mV. rMax corresponds to half the total
        // number of large boxes, rMax at top and -rMax at bottom
        RectF gridRect = mPlot.getGraph().getGridRect();
        double rMax = .25 * N_DOMAIN_LARGE_BOXES * gridRect.height()
                / gridRect.width();

        // None is the default, but set it explicitly anyway
        mPlot.getGraph().setLineLabelEdges(XYGraphWidget.Edge.NONE);

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
        updateDomainBoundaries();
        // Set the domain block to be .2 * nlarge so large block will be
        // nLarge samples
        mPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, .2 * N_LARGE);
        mPlot.setLinesPerDomainLabel(5);

//        // Allow panning
//        PanZoom.attach(mPlot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom.NONE);

        // Debug
        Log.d(TAG, this.getClass().getSimpleName()
                + " setupPlot: Before update\n" + getLogInfo(rMax));

        // Update the plot
        update();
    }

    public String getLogInfo(double rMax) {
        RectF gridRect = mPlot.getGraph().getGridRect();
        StringBuilder sb = new StringBuilder();
        sb.append("    renderMode=").append(mPlot.getRenderMode()
                == Plot.RenderMode.USE_MAIN_THREAD ? "Main" : "Background")
                .append("\n");
        DisplayDimensions dims = mPlot.getDisplayDimensions();
        sb.append("    view LRTB=").append(mPlot.getLeft()).append(",").
                append(mPlot.getRight()).append(",")
                .append(mPlot.getTop()
                ).append(",").append(mPlot.getBottom()).append("\n");
        sb.append("    canvasRect LRTB=").append(dims.canvasRect.left)
                .append(",").append(dims.canvasRect.right)
                .append(",").append(dims.canvasRect.top)
                .append(",").append(dims.canvasRect.bottom)
                .append("\n");
        sb.append("    marginatedRect LRTB=").append(dims.marginatedRect.left)
                .append(",").append(dims.marginatedRect.right).append(",")
                .append(dims.marginatedRect.top).append(",")
                .append(dims.marginatedRect.bottom).append("\n");
        sb.append("    paddedRect LRTB=")
                .append(dims.paddedRect.left).append(",")
                .append(dims.paddedRect.right).append(",")
                .append(dims.paddedRect.top).append(",").
                append(dims.paddedRect.bottom).append("\n");
        sb.append("    gridRect LRTB=").append(gridRect.left).append(",")
                .append(gridRect.right).append(",")
                .append(gridRect.top).append(",").append(gridRect.bottom)
                .append("\n");
        sb.append("    gridRect width=").append(gridRect.width())
                .append(" height=").append(gridRect.height()).append("\n");
        DisplayMetrics displayMetrics = mActivity.getResources()
                .getDisplayMetrics();
        sb.append("    display widthPixels=").append(displayMetrics.widthPixels)
                .append(" heightPixels=").append(displayMetrics.heightPixels)
                .append("\n");
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
        double screenTop = mPlot.seriesToScreenY(mPlot.getBounds().getMaxY());
        double screenBottom =
                mPlot.seriesToScreenY(mPlot.getBounds().getMinY());
        sb.append("    screenY(rMax)=").append(screenYMax).append(" screenY")
                .append("(-rMax)=").append(screenYMin)
                .append(" screenY(top)=").append(screenTop)
                .append(" screenY(bottom)=").append(screenBottom).append("\n");
//        sb.append("    layoutRequested=").append(mPlot.isLayoutRequested())
//                .append(" isLaidOut=").append(mPlot.isLaidOut())
//                .append(" isInLayout=").append(mPlot.isInLayout())
//                .append("\n");

        // ECG Specific
        sb.append("    mDataIndex=").append(mDataIndex).append(" mSeries: " +
                "size=").append(mSeries.getxVals().size()).append(
                "\n");
        sb.append("    Time=").append(ECGActivity.sdfshort.format(new Date()))
                .append(" mOrientationChangedECG=")
                .append(mActivity.mOrientationChangedECG)
                .append(" height=").append(mPlot.getHeight()).append(" " +
                "isLaidOut=")
                .append(mPlot.isLaidOut()).append("\n");

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
//        Log.d(TAG, this.getClass().getSimpleName()
//                + " update: thread: " + Thread.currentThread()
//                .getName());
//        Log.d(TAG, "ECGPLot update: " + ECGActivity.sdfshort.format(new Date())
//                + " mOrientationChangedECG=" + mActivity.mOrientationChangedECG
//                + " height=" + mPlot.getHeight()
//                + " isLaidOut=" + mPlot.isLaidOut()
//                + " plotter=" + Utils.getHashCode(this)
//                + " plot=" + Utils.getHashCode(mPlot)
//        );
        mActivity.runOnUiThread(mPlot::redraw);
    }

    /**
     * Gets info about the view.
     */
    private String getPlotInfo() {
        final String LF = "\n";
        StringBuilder sb = new StringBuilder();
        if (mPlot == null) {
            sb.append("Plot is null");
            return sb.toString();
        }
        sb.append("Title=").append(mPlot.getTitle().getText()).append(LF);
        sb.append("Range Title=").append(mPlot.getRangeTitle().getText()).append(LF);
        sb.append("Domain Title=").append(mPlot.getDomainTitle().getText()).append(LF);
        sb.append("Range Origin=").append(mPlot.getRangeOrigin()).append(LF);
        long timeVal = mPlot.getDomainOrigin().longValue();
        Date date = new Date(timeVal);
        sb.append("Domain Origin=").append(date.toString()).append(LF);
        sb.append("Range Step Value=").append(mPlot.getRangeStepValue()).append(LF);
        sb.append("Domain Step Value=").append(mPlot.getDomainStepValue()).append(LF);
        sb.append("Graph Width=").append(mPlot.getGraph().getSize().getWidth().getValue()).append(LF);
        sb.append("Graph Height=").append(mPlot.getGraph().getSize().getHeight().getValue()).append(LF);
        sb.append("mDataIndex=").append(mDataIndex).append(LF);
        if (mSeries != null) {
            if (mSeries.getxVals() != null) {
                sb.append("mSeries Size=").append(mSeries.getxVals().size()).append(LF);
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
