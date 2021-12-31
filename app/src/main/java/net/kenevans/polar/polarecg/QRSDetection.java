package net.kenevans.polar.polarecg;

import com.polar.sdk.api.model.PolarEcgData;

import java.util.List;

public class QRSDetection implements IQRSConstants {

    private ECGActivity mActivity;
    private ECGPlotter mECGPlotter;
    private HRPlotter mHRPlotter;
    private QRSPlotter mQRSPlotter;

    public QRSDetection(ECGActivity activity, ECGPlotter ecgPlotter,
                        HRPlotter hrPlotter, QRSPlotter qrsPlotter) {
        mActivity = activity;
        mECGPlotter = ecgPlotter;
        mHRPlotter = hrPlotter;
        mQRSPlotter = qrsPlotter;
    }

    public void process(PolarEcgData polarEcgData) {
        // Update the ECG plot
        mECGPlotter.addValues(polarEcgData);

        // samples is the ecg value in μV, mv = .001 * μV;
        for (Integer val : polarEcgData.samples) {
            doAlgorithm(val);
        }
    }

    /**
     * Runs the QRS detection algorithm on the given ECG value.
     *
     * @param val The value to process.
     */
    public void doAlgorithm(double val) {
        double dummy = 0;
        mQRSPlotter.addValues(val, dummy);
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
        return 0;
    }

    /**
     * Scores a value to 0 or 1 depending on if is below the threshold or not.
     *
     * @param val       The value.
     * @param threshold The threshold.
     * @return The score.
     */
    public double score(double val, double threshold) {
        if (val < threshold) return 0;
        return 1;
    }
}
