package net.kenevans.polar.polarecg;

import com.polar.sdk.api.model.PolarEcgData;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class QRSDetection implements IConstants, IQRSConstants {

    private final ECGActivity mActivity;

    private final FixedSizeList<Integer> mPeakIndices =
            new FixedSizeList<>(DATA_WINDOW);
    private int mPeakIndex = -1;
    private int mMinPeakIndex = -1;
    private int mMaxPeakIndex = -1;

    // Initialize these with observed values
    private static final double STAT_INITIAL_MEAN = .00;
    private static final double STAT_INITIAL_STDDEV = .1;
    private static final double N_SIGMA = 1.0;
    private static final int SEARCH_EXTEND = 2;

    double mSumVals = STAT_INITIAL_MEAN * HR_200_INTERVAL;
    double mSumSq = (STAT_INITIAL_STDDEV * STAT_INITIAL_STDDEV -
            STAT_INITIAL_MEAN * STAT_INITIAL_MEAN) * HR_200_INTERVAL;
    int mNStat = HR_200_INTERVAL;
    double mMean = STAT_INITIAL_MEAN;
    double mStdDev = STAT_INITIAL_STDDEV;
    double mThreshold = mMean + N_SIGMA * mStdDev;

    private int mNSamples = 0;
    private double mStartTime = Double.NaN;

    // These keep track of the lowest and highest ECG values in the scoring
    // window. The max should correspond to R and the min to S. The normal
    // duration (interval) of the QRS complex is between 0.08 and 0.10
    // seconds When the duration is between 0.10 and 0.12 seconds, it is
    // intermediate or slightly prolonged. A QRS duration of greater than
    // 0.12 seconds is considered abnormal. 0.12 ms = 16 samples. 0.10
    // samples = 13 samples.

//    private final FixedSizeList<Double> curButterworth =
//            new FixedSizeList<>(DATA_WINDOW);
    private final FixedSizeList<Double> curDeriv =
            new FixedSizeList<>(DATA_WINDOW);
    private final FixedSizeList<Double> curScore =
            new FixedSizeList<>(DATA_WINDOW);
    private final FixedSizeList<Double> curEcg =
            new FixedSizeList<>(DATA_WINDOW);

    private final List<Double> ecgVals = new ArrayList<>();

    /**
     * Moving average of the RR. Used to get the HR as 60 / avgRR.
     */
    private final RunningAverage movingAverageRr =
            new RunningAverage(MOV_AVG_HR_WINDOW);

    public QRSDetection(ECGActivity activity) {
        mActivity = activity;
    }

    public void process(PolarEcgData polarEcgData) {
//        Log.d(TAG, this.getClass().getSimpleName() + " process"
//                + " thread=" + Thread.currentThread().getName());
        // Update the ECG plot
        ecgPlotter().addValues(polarEcgData);

        // samples contains the ecgVals values in μV, mv = .001 * μV;
        for (Integer val : polarEcgData.samples) {
            doAlgorithm(MICRO_TO_MILLI_VOLT * val);
        }
    }

    /**
     * Runs the QRS detection algorithm on the given ECG value.
     *
     * @param ecg The value to process.
     */
    public void doAlgorithm(double ecg) {
        // Record the start time as now.
        if (Double.isNaN(mStartTime)) mStartTime = new Date().getTime();
        ecgVals.add(ecg);
        mNSamples++;
        curEcg.add(ecg);

        FixedSizeList<Double> input;
        double doubleVal, hr, rr;
        double variance;

//        // Butterworth
//        input = curEcg;
//        if (curButterworth.size() == DATA_WINDOW) curButterworth.remove(0);
//        curButterworth.add(0.);    // Doesn't matter
//        doubleVal = filter(A_BUTTERWORTH3, B_BUTTERWORTH3, input,
//                curButterworth);
//        curButterworth.set(curButterworth.size() - 1, doubleVal);

        // Derivative (Only using positive part)
        input = curEcg;
        curDeriv.add(0.);    // Doesn't matter
        doubleVal = filter(A_DERIVATIVE, B_DERIVATIVE, input,
                curDeriv);
        doubleVal = Math.max(doubleVal, 0);
        curDeriv.set(curDeriv.size() - 1, doubleVal);

        // Score
        mNStat++;
        mSumVals += doubleVal;
        mSumSq += doubleVal * doubleVal;
        mMean = mSumVals / mNStat;
        variance = mSumSq / mNStat + mMean * mMean;
        mStdDev = Math.sqrt(variance);
        mThreshold = mMean + N_SIGMA * mStdDev;
        curScore.add(mThreshold);

        double val, maxEcg, lastMaxEcgVal;
        double scoreval;
        int lastIndex, lastPeakIndex, startSearch, endSearch;

        input = curDeriv;
        int i = mNSamples - 1;

        // Process finding the peaks
        if (i % HR_200_INTERVAL == 0) {
            // End of interval, process this interval
            if (i > 0 && mPeakIndex != -1) {
//                Log.d(TAG, "doAlgorithm: " +
//                        ".......... start processing interval i=" + i
//                        + " mPeakIndex=" + mPeakIndex
//                        + " mMinPeakIndex=" + mMinPeakIndex
//                        + " mMaxPeakIndex=" + mMaxPeakIndex
//                );
                // There is an mPeakIndex != -1
                // Look between mMinPeakIndex and mMaxPeakIndex for a the
                // largest ecg value
                startSearch = Math.max(i - HR_200_INTERVAL, mMinPeakIndex);
                if (startSearch < 0) startSearch = 0;
                endSearch = Math.min(i, mMaxPeakIndex + SEARCH_EXTEND);
                maxEcg = -Double.MAX_VALUE;
//                Log.d(TAG, "doAlgorithm: " +
//                        ".......... start searching: startSearch="
//                        + startSearch
//                        + " endSearch=" + endSearch);
                for (int i1 = startSearch; i1 < endSearch + 1; i1++) {
                    if (ecgVals.get(i1) > maxEcg) {
                        maxEcg = ecgVals.get(i1);
                        mPeakIndex = i1;
                    }
                } // End of search
//                Log.d(TAG, "doAlgorithm: " +
//                        ".......... end searching: startSearch="
//                        + startSearch
//                        + " endSearch=" + endSearch
//                        + " mPeakIndex=" + mPeakIndex
//                        + " maxEcg=" + maxEcg
//                );
                // Check if there is a close one in the previous interval
                if (mPeakIndices.size() > 0) {
                    lastIndex = mPeakIndices.size() - 1; // last
                    // index in mPeakIndices
                    lastPeakIndex = mPeakIndices.get(lastIndex);
                    if (mPeakIndex - lastPeakIndex < HR_200_INTERVAL) {
                        lastMaxEcgVal = ecgVals.get(lastPeakIndex);
                        if (maxEcg >= lastMaxEcgVal) {
                            // Replace the old one
                            mPeakIndices.setLast(mPeakIndex);
                            qrsPlotter().replaceLastPeakValue(mPeakIndex,
                                    maxEcg);
//                            Log.d(TAG, "doAlgorithm: " +
//                                    "replaceLastPeakValue:"
//                                    + " mPeakIndex=" + mPeakIndex
//                                    + ", maxEcg=" + maxEcg);
                        }
                    } else {
                        // Is not near a previous one, add it
                        mPeakIndices.add(mPeakIndex);
                        qrsPlotter().addPeakValue(mPeakIndex,
                                maxEcg);
//                        Log.d(TAG, "doAlgorithm: " +
//                                "addPeakValue:"
//                                + " mPeakIndex=" + mPeakIndex
//                                + ", maxEcg=" + maxEcg);
                    }
                } else {
                    // First peak
                    mPeakIndices.add(mPeakIndex);
                    qrsPlotter().addPeakValue(mPeakIndex, maxEcg);
//                    Log.d(TAG, "doAlgorithm: " +
//                            "addPeakValue:"
//                            + " mPeakIndex=" + mPeakIndex
//                            + ", maxEcg=" + maxEcg);
                }

                // Do HR/RR plot
                if (mPeakIndices.size() > 1) {
                    rr = 1000 / FS * (mPeakIndex - mPeakIndices.get(mPeakIndices.size() - 2));
                    hr = 60000. / rr;
                    if (!Double.isInfinite(hr)) {
//                    movingAverageHr.add(hr);
                        movingAverageRr.add(rr);
                        // Wait to start plotting until HR average is well
                        // defined
                        if (movingAverageRr.size() >= MOV_AVG_HR_WINDOW) {
//                        hrPlotter().addValues2(mStartTime + 1000 *
//                        mMaxIndex / FS,
//                                movingAverageHr.average(), rr);
                            hrPlotter().addValues2(mStartTime + 1000 * mPeakIndex / FS,
                                    60000. / movingAverageRr.average(), rr);
                            hrPlotter().fullUpdate();
                        }
                    }
                }
            }
            // Start a new interval
            mPeakIndex = -1;
            mMinPeakIndex = -1;
            mMaxPeakIndex = -1;
//            Log.d(TAG, "doAlgorithm: " +
//                    ".......... end processing interval i=" + i);
        } // End of end of process interval

        // Check for max ecg
        val = input.getLast();
        scoreval = curScore.getLast();
        if (val > scoreval) {
            mPeakIndex = i;
            if (mPeakIndex > mMaxPeakIndex)
                mMaxPeakIndex = mPeakIndex;
            if (mMinPeakIndex == -1 || mPeakIndex < mMinPeakIndex)
                mMinPeakIndex = mPeakIndex;
        }

        // Plot
        // Multipliers on curSquare and curScore should be the same
        double scale_factor = 5;
        qrsPlotter().addValues(ecg, scale_factor * curDeriv.getLast(),
                scale_factor * curScore.getLast());
    }

    /**
     * Calculates a result for a generalized filter with coefficients a and b.
     * Returns 0 if x and y are not long enough to provide sufficient values
     * for the sums over the coefficients.
     * <p>
     * y[n] = 1 / a[0] * (suma - sumb)
     * suma = sum from 1 to q of a[j] * y[n-j], q = len(a)
     * sumb = sum from 0 to p of b[j] * x[n-j], p = len(b)
     * Uses the values at the ends of x and y.
     *
     * @param a The A filter coefficients.
     * @param b The B filter coefficients.
     * @param x x values for the filter.
     * @param y y values for the filter.
     * @return The new value.
     */
    public double filter(double[] a, double[] b, List<Double> x,
                         List<Double> y) {
        // TODO Consider handling lenx < lenb and leny < lena differently
        //  than exit
        int lena = a.length;
        int lenb = b.length;
        int lenx = x.size();
        int leny;
        if (lenx < lenb) return (0);
        double suma = 0;
        if (y != null) {
            leny = y.size();
            if (leny < lena) return (0);
            for (int i = 0; i < lena; i++) {
                suma += a[i] * y.get(leny - i - 1);
            }
        }
        double sumb = 0;
        for (int i = 0; i < lenb; i++) {
            sumb += b[i] * x.get(lenx - i - 1);
        }
        return (sumb - suma) / a[0];
    }

    public ECGPlotter ecgPlotter() {
        return mActivity.mECGPlotter;
    }

    public QRSPlotter qrsPlotter() {
        return mActivity.mQRSPlotter;
    }

    public HRPlotter hrPlotter() {
        return mActivity.mHRPlotter;
    }
}
