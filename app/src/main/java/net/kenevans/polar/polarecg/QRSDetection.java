package net.kenevans.polar.polarecg;

import com.polar.sdk.api.model.PolarEcgData;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class QRSDetection implements IConstants, IQRSConstants {

    private ECGActivity mActivity;
    private ECGPlotter mECGPlotter;
    private HRPlotter mHRPlotter;
    private QRSPlotter mQRSPlotter;

    private FixedSizeList<Double> mPeaks = new FixedSizeList<>(DATA_WINDOW);
    private FixedSizeList<Integer> mPeakIndices =
            new FixedSizeList<>(DATA_WINDOW);
    private FixedSizeList<Double> mMaxAvg = new FixedSizeList<>(DATA_WINDOW);
    private FixedSizeList<Double> mMaxAvgIndices =
            new FixedSizeList<>(DATA_WINDOW);
    boolean mScoring = false;
    int mScoreStart = -1;
    int mScoreEnd = -1;

    private int mNSamples = 0;
    private double mStartTime = Double.NaN;

    // These keep track of the lowest and highest ECG values in the scoring
    // window. The max should correspond to R and the min to S. The normal
    // duration (interval) of the QRS complex is between 0.08 and 0.10
    // seconds When the duration is between 0.10 and 0.12 seconds, it is
    // intermediate or slightly prolonged. A QRS duration of greater than
    // 0.12 seconds is considered abnormal. 0.12 ms = 16 samples. 0.10
    // samples = 13 samples.
    double mMaxEcg = -Double.MAX_VALUE;
    double mMinEcg = Double.MAX_VALUE;
    int mMaxIndex = -1;
    int mMinIndex = -1;

    double mMaxAvgHeight = MOV_AVG_HEIGHT_DEFAULT;

    private FixedSizeList<Double> curButterworth =
            new FixedSizeList<>(DATA_WINDOW);
    private FixedSizeList<Double> curDeriv = new FixedSizeList<>(DATA_WINDOW);
    private FixedSizeList<Double> curSquare = new FixedSizeList<>(DATA_WINDOW);
    private FixedSizeList<Double> curAvg = new FixedSizeList<>(DATA_WINDOW);
    private FixedSizeList<Double> curScore = new FixedSizeList<>(DATA_WINDOW);
    private FixedSizeList<Double> curEcg = new FixedSizeList<>(DATA_WINDOW);

    private List<Double> ecgVals = new ArrayList<>();

    /**
     * Moving average of the data.
     */
    private RunningAverage movingAverage = new RunningAverage(MOV_AVG_WINDOW);

    /**
     * Moving average of the HR.
     */
    private RunningAverage movingAverageHr =
            new RunningAverage(MOV_AVG_HR_WINDOW);

    /**
     * Moving average of the moving average heights
     */
    private RunningAverage movingAverageHeight =
            new RunningAverage(MOV_AVG_HEIGHT_WINDOW);

    double threshold;

    public QRSDetection(ECGActivity activity, ECGPlotter ecgPlotter,
                        HRPlotter hrPlotter, QRSPlotter qrsPlotter) {
        mActivity = activity;
        mECGPlotter = ecgPlotter;
        mHRPlotter = hrPlotter;
        mQRSPlotter = qrsPlotter;

        //Initialize movingAverageHeight assuming avg peaks are
        // MOV_AVG_HEIGHT_DEFAULT high. These values will pre-dispose the
        // average in the beginning.
        for (int i = 0; i < MOV_AVG_HEIGHT_WINDOW; i++) {
            movingAverageHeight.add(MOV_AVG_HEIGHT_DEFAULT);
        }
    }

    public void process(PolarEcgData polarEcgData) {
//        Log.d(TAG, this.getClass().getSimpleName() + " process"
//                + " thread=" + Thread.currentThread().getName());
        // Update the ECG plot
        mECGPlotter.addValues(polarEcgData);

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

        FixedSizeList<Double> input;
        double doubleVal, peakEcgVal, hr, rr;
        threshold =
                MOV_AVG_HEIGHT_THRESHOLD_FACTOR * movingAverageHeight.average();
        curEcg.add(ecg);

        // Butterworth
        input = curEcg;
        if (curButterworth.size() == DATA_WINDOW) curButterworth.remove(0);
        curButterworth.add(0.);    // Doesn't matter
        doubleVal = filter(A_BUTTERWORTH3, B_BUTTERWORTH3, input,
                curButterworth);
        curButterworth.set(curButterworth.size() - 1, doubleVal);

        // Derivative
        input = curButterworth;
        curDeriv.add(0.);    // Doesn't matter
        doubleVal = filter(A_DERIVATIVE, B_DERIVATIVE, input,
                curDeriv);
        curDeriv.set(curDeriv.size() - 1, doubleVal);

        // Square
        input = curDeriv;
        curSquare.add(0.);    // Doesn't matter
        doubleVal = input.get(curDeriv.size() - 1);
        doubleVal *= doubleVal;
        curSquare.set(curSquare.size() - 1, doubleVal);

        // Moving average
        input = curSquare;
        curAvg.add(0.);    // Doesn't matter
        doubleVal = input.get(input.size() - 1);
        movingAverage.add(doubleVal);
        doubleVal = movingAverage.average();
        curAvg.set(curAvg.size() - 1, doubleVal);

        // Score
        input = curAvg;
        int scoreVal;
        double last;
        int i = mNSamples - 1;
        // Base the threshold on the current average of the moving avg heights
        // input.getLast = cur_avg.getLast is the last value of cur_avg
        scoreVal = score(input.getLast(), threshold);
        curScore.add((double) scoreVal);
        // Process finding the peaks
        if (mScoring && scoreVal == 0) {
            mScoreEnd = i;
            mScoring = false;
        }
        if (!mScoring && scoreVal == 1) {
            mScoreStart = i;
            mScoring = true;
        }
        if (!mScoring && mScoreEnd == i && mMaxIndex != -1) {
            // End of interval, process the score
            peakEcgVal = ecgVals.get(mMaxIndex);
            mPeaks.add(peakEcgVal);
            mPeakIndices.add(mMaxIndex);
            // Do HR/RR plot
            if (mPeaks.size() > 1) {
                rr = 1000 / FS * (mMaxIndex - mPeakIndices.get(mPeaks.size() - 2));
                hr = 60000. / rr;
                if (!Double.isInfinite(hr)) {
                    movingAverageHr.add(hr);
                    // Wait to start plotting until HR average is well defined
                    if (movingAverageHr.size() >= MOV_AVG_HR_WINDOW) {
                        mHRPlotter.addValues2(mStartTime + 1000 * mMaxIndex / FS,
                                movingAverageHr.average(), rr);
                        mHRPlotter.fullUpdate();
                    }
                }
            }
            // Don't count this one if the interval between R and S is too long
            if (mMaxIndex - mMinIndex <= MAX_QRS_LENGTH) {
                // Do QRS plot
                mQRSPlotter.addPeakValue(mMaxIndex, peakEcgVal);
                // Recalculate the threshold
                movingAverageHeight.add(mMaxAvgHeight);
                threshold = MOV_AVG_HEIGHT_THRESHOLD_FACTOR
                        * movingAverageHeight.average();
            }
            // Reset
            mMaxIndex = mMinIndex = -1;
            mMaxEcg = -Double.MAX_VALUE;
            mMinEcg = -Double.MAX_VALUE;
        }
        if (mScoring) {
            if (mScoreStart == i) {
                // Start of interval, set up mScoring
                if (i >= SCORE_OFFSET) {
                    mMaxIndex = mMinIndex = i - SCORE_OFFSET;
                    mMaxEcg = mMinEcg = ecgVals.get(i - SCORE_OFFSET);
                } else {
                    mMaxIndex = mMinIndex = -1;
                    mMaxEcg = -Double.MAX_VALUE;
                    mMinEcg = -Double.MAX_VALUE;
                }
                mMaxAvgHeight = input.getLast();
            } else {
                // In interval, accumulate data
                if (i >= SCORE_OFFSET) {
                    last = ecgVals.get(i - SCORE_OFFSET);
                    if (last > mMaxEcg) {
                        mMaxEcg = last;
                        mMaxIndex = i - SCORE_OFFSET;
                    }
                    if (last < mMinEcg) {
                        mMinEcg = last;
                        mMinIndex = i - SCORE_OFFSET;
                    }
                    last = input.getLast();
                    if (last > mMaxAvgHeight) {
                        mMaxAvgHeight = last;
                    }
                }
            }
        }

        // Plot
        doubleVal = movingAverage.average();
//        Log.d(TAG, String.format("ecg=%.3f avg=%.3f score=%d threshold=%.3f",
//                ecg, doubleVal, scoreVal, threshold));
//        Log.d(TAG, "i=" + i + " " + scoreVal + " mScoring=" + mScoring
//                + " mScoreStart=" + mScoreStart
//                + " mScoreEnd=" + mScoreEnd);
        mQRSPlotter.addValues(ecg, 10 * doubleVal, 0.5 * (double) scoreVal);
//        mQRSPlotter.addValues(ecg, null, 0.5 * (double) scoreVal);
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

    /**
     * Scores a value to 0 or 1 depending on if is below the threshold or not.
     *
     * @param val       The value.
     * @param threshold The threshold.
     * @return The score.
     */
    public int score(double val, double threshold) {
        if (val < threshold) return 0;
        return 1;
    }
}
