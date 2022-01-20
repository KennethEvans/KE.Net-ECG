package net.kenevans.polar.polarecg;

import android.graphics.Color;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

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

import java.util.Date;
import java.util.Locale;

@SuppressWarnings("WeakerAccess")
public class QRSPlotter implements IConstants, IQRSConstants {
    private ECGActivity mActivity;
    private XYPlot mPlot;

    private XYSeriesFormatter<XYRegionFormatter> mFormatter1;
    private SimpleXYSeries mSeries1;
    private XYSeriesFormatter<XYRegionFormatter> mFormatter2;
    private SimpleXYSeries mSeries2;
    private XYSeriesFormatter<XYRegionFormatter> mFormatter3;
    private SimpleXYSeries mSeries3;
    private XYSeriesFormatter<XYRegionFormatter> mFormatter4;
    private SimpleXYSeries mSeries4;

    /**
     * The next index in the data
     */
    private long mDataIndex;

    /**
     * CTOR that just sets the plot.
     *
     * @param plot The XYPlot.
     */
    public QRSPlotter(XYPlot plot) {
        this.mPlot = plot;
        // Don't do anything else
    }

    public QRSPlotter(ECGActivity activity, XYPlot plot) {
        Log.d(TAG, this.getClass().getSimpleName() + " QRSPlotter CTOR");
        // This is the mActivity, needed for resources
        this.mActivity = activity;
        this.mPlot = plot;
        this.mDataIndex = 0;

        mFormatter1 = new LineAndPointFormatter(Color.rgb(0, 153, 255),
                null, null, null);
        mFormatter1.setLegendIconEnabled(false);
        mSeries1 = new SimpleXYSeries("Series 1");
        mPlot.addSeries(mSeries1, mFormatter1);

        mFormatter2 = new LineAndPointFormatter(Color.YELLOW,
                null, null, null);
        mFormatter2.setLegendIconEnabled(false);
        mSeries2 = new SimpleXYSeries("Series 2");
        mPlot.addSeries(mSeries2, mFormatter2);

        mFormatter3 = new LineAndPointFormatter(Color.GREEN,
                null, null, null);
        mFormatter3.setLegendIconEnabled(false);
        mSeries3 = new SimpleXYSeries("Series 3");
        mPlot.addSeries(mSeries3, mFormatter3);

        mFormatter4 = new LineAndPointFormatter(null,
                Color.RED, null, null);
        mFormatter4.setLegendIconEnabled(false);
        mSeries4 = new SimpleXYSeries("Series 4");
        mPlot.addSeries(mSeries4, mFormatter4);

        setupPlot();
    }

    /**
     * Get a new QRSPLotter instance, using the given XYPlot but other values
     * from the current one. Use for replacing the current plotter.
     *
     * @param plot The XYPLot.
     * @return The new instance.
     */
    public QRSPlotter getNewInstance(XYPlot plot) {
        QRSPlotter newPlotter = new QRSPlotter(plot);
        newPlotter.mPlot = plot;
        newPlotter.mActivity = this.mActivity;
        newPlotter.mDataIndex = this.mDataIndex;

        newPlotter.mFormatter1 = this.mFormatter1;
        newPlotter.mSeries1 = this.mSeries1;
        newPlotter.mPlot.addSeries(mSeries1, mFormatter1);

        newPlotter.mFormatter2 = this.mFormatter2;
        newPlotter.mSeries2 = this.mSeries2;
        newPlotter.mPlot.addSeries(mSeries2, mFormatter2);

        newPlotter.mFormatter3 = this.mFormatter3;
        newPlotter.mSeries3 = this.mSeries3;
        newPlotter.mPlot.addSeries(mSeries3, mFormatter3);

        newPlotter.mFormatter4 = this.mFormatter4;
        newPlotter.mSeries4 = this.mSeries4;
        newPlotter.mPlot.addSeries(mSeries3, mFormatter3);

        newPlotter.setupPlot();
        return newPlotter;
    }

    /**
     * Sets the plot parameters, calculating the range boundaries to have the
     * same grid as the domain.  Calls update when done.
     */
    public void setupPlot() {
        Log.d(TAG, this.getClass().getSimpleName() + " setupPlot");
        if (mPlot.getVisibility() == View.GONE) return;

        // Calculate the range limits to make the blocks be square
        // Using .5 mV and nLarge / samplingRate for total grid size
        // rMax is half the total, rMax at top and -rMax at bottom
        RectF gridRect = mPlot.getGraph().getGridRect();
        // Note different from ECG plot
        double rMax =
                .125 * (gridRect.bottom - gridRect.top) * N_ECG_PLOT_POINTS /
                        N_LARGE / (gridRect.right - gridRect.left);

        // None is the default, but set it explicitly anyway
        mPlot.getGraph().setLineLabelEdges(XYGraphWidget.Edge.NONE);

        // Set the domain boundaries (range boundaries are fixed)
        updateDomainBoundaries();

        // Range
        mPlot.setRangeBoundaries(-rMax, rMax, BoundaryMode.FIXED);
        // Set the range block to be .1 mV so a large block will be .5 mV
        mPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, .5);
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
        // Set the domain block to be .2 * N_LARGE so large block will be
        // nLarge samples
        mPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, N_LARGE);

//        // Allow panning
//        PanZoom.attach(mPlot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom.NONE);

//        // Debug
//        Log.d(TAG, this.getClass().getSimpleName()
//                + " setupPlot: Before update\n" + getLogInfo(rMax));

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

        // QRS Specific
        sb.append("    mDataIndex=").append(mDataIndex)
                .append(" mSeries: size=").append(mSeries1.getxVals().size())
                .append(",").append(mSeries2.getxVals().size())
                .append(",").append(mSeries3.getxVals().size())
                .append(",").append(mSeries4.getxVals().size()).append("\n");
        sb.append("    Time=").append(ECGActivity.sdfshort.format(new Date()))
                .append(" mOrientationChangedQRS=")
                .append(mActivity.mOrientationChangedQRS)
                .append(" height=").append(mPlot.getHeight()).append(" " +
                "isLaidOut=")
                .append(mPlot.isLaidOut()).append("\n");

        return sb.toString();
    }

    /**
     * Implements a strip chart adding new data at the end.
     *
     * @param val1 Value for the first series.
     * @param val2 Value for the second series.
     * @param val3 Value for the third series.
     */
    public void addValues(Number val1, Number val2, Number val3) {
//        Log.d(TAG, this.getClass().getSimpleName()
//                + "addValues: dataIndex=" + mDataIndex + " mSeriesSize="
//                + mSeries1.size() + " mSeries2Size=" + mSeries2.size()
//                + " val1=" + val1 + " val2=" + val2);

        if (mPlot.getVisibility() == View.GONE) return;

        // Add the new values, removing old values if needed
        // Convert from  μV to mV
        if (val1 != null) {
            if (mSeries1.size() >= N_TOTAL_POINTS) {
                mSeries1.removeFirst();
            }
            mSeries1.addLast(mDataIndex, val1);
        }

        if (val2 != null) {
            if (mSeries2.size() >= N_TOTAL_POINTS) {
                mSeries2.removeFirst();
            }
            mSeries2.addLast(mDataIndex, val2);
        }

        if (val3 != null) {
            if (mSeries3.size() >= N_TOTAL_POINTS) {
                mSeries3.removeFirst();
            }
            mSeries3.addLast(mDataIndex, val3);
        }

        mDataIndex++;
        // Reset the domain boundaries
        updateDomainBoundaries();
        update();
    }

    public void addPeakValue(int sample, double ecg) {
//        Log.d(TAG, this.getClass().getSimpleName()
//                + "addPeakValue: dataIndex=" + mDataIndex + " mSeriesSize="
//                + mSeries4.size()
//                + " sample=" + sample + " ecg=" + ecg);
//
        if (mPlot.getVisibility() == View.GONE) return;

        // Remove old values if needed
        long xMin = mDataIndex - N_TOTAL_POINTS;
        while (mSeries4.size() > 0 && (int) mSeries4.getxVals().getFirst() < xMin) {
            mSeries4.removeFirst();
//                Log.d(TAG, "sample=" + sample + " deleted="
//                + mSeries4.getxVals().getFirst());
        }
        mSeries4.addLast(sample, ecg);
//        Log.d(TAG, "added sample=" + sample + " size=" + mSeries4.size()
//                + " xmin=" + xMin + " mDataIndex=" + mDataIndex);
    }

    public void updateDomainBoundaries() {
        if (mPlot.getVisibility() == View.GONE) return;
        long plotMin, plotMax;
        plotMin = mDataIndex - N_ECG_PLOT_POINTS;
        plotMax = mDataIndex;
        mPlot.setDomainBoundaries(plotMin, plotMax, BoundaryMode.FIXED);
//        Log.d(TAG, this.getClass().getSimpleName() + "
//        updateDomainBoundaries: "
//                + "plotMin=" + plotMin + " plotmax=" + plotMax
//                + " size=" + mSeries1.size());
//        int colorInt = mPlot.getGraph().getGridBackgroundPaint().getColor();
//        String hexColor = String.format("#%06X", (0xFFFFFF & colorInt));
//        Log.d(TAG, "gridBgColor=" + hexColor);
    }

    /**
     * Updates the plot. Runs on the UI thread.
     */
    public void update() {
        if (mPlot.getVisibility() == View.GONE) return;
        //            Log.d(TAG, this.getClass().getSimpleName()
        //                    + " update: thread: " + Thread.currentThread()
        //                    .getName());
        if (mDataIndex % 73 == 0) {
            mActivity.runOnUiThread(mPlot::redraw);
        }
    }

    /**
     * Clears the plot and resets dataIndex.
     */
    public void clear() {
        if (mPlot.getVisibility() == View.GONE) return;
        mDataIndex = 0;
        mSeries1.clear();
        mSeries2.clear();
        mSeries3.clear();
        mSeries4.clear();
        update();
    }
}
