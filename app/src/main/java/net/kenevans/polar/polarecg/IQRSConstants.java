package net.kenevans.polar.polarecg;

interface IQRSConstants {
    /**
     * Sampling rate. These algorithms are based on this particular sampling
     * rate.
     */
    double FS = 130.0;
    /**
     * Number of small boxes in a large box.
     */
    int N_SMALL_BOXES_PER_LARGE_BOX = 5;
    /**
     * The number of samples in a large box on an ECG plot.
     */
    int N_LARGE = (int) (FS / N_SMALL_BOXES_PER_LARGE_BOX); // =26
    /**
     * The total number of points to keep.
     */
    int N_TOTAL_POINTS = (int) (30 * FS);  // =3900 -> 30 sec
    /**
     * The number of points to show in an ECG plot.
     */
    int N_ECG_PLOT_POINTS = 4 * N_SMALL_BOXES_PER_LARGE_BOX * N_LARGE;
    // =520 points -> 4 sec
    /**
     * Number of large boxes visible on the x axis.
     */
    int N_DOMAIN_LARGE_BOXES = N_ECG_PLOT_POINTS / N_LARGE; // = 20
    /**
     * Ratio of mV to mm for a box.
     */
    int RATIO_MM_MV = 100;

    /**
     * Data window size.  Must be large enough for maximum number of
     * coefficients.
     */
    int DATA_WINDOW = 20;
    /**
     * Moving average data window size.
     */
    int MOV_AVG_WINDOW = 20;
    /**
     * Moving average HR window size.
     */
    int MOV_AVG_HR_WINDOW = 25;
    /**
     * Moving average height window size.
     */
    int MOV_AVG_HEIGHT_WINDOW = 5;
    /**
     * Moving average height default.
     */
    double MOV_AVG_HEIGHT_DEFAULT = .025;
    /**
     * Moving average height threshold factor.
     * Note: threshold = MOV_AVG_HEIGHT_THRESHOLD_FACTOR * Moving_average.avg()
     */
    double MOV_AVG_HEIGHT_THRESHOLD_FACTOR = .4;
    /**
     * The maximum number of samples between R and S. The normal
     * duration (interval) of the QRS complex is between 0.08 and 0.10
     * seconds When the duration is between 0.10 and 0.12 seconds, it is
     * intermediate or slightly prolonged. A QRS duration of greater than
     * 0.12 seconds is considered abnormal.
     */
    int MAX_QRS_LENGTH = (int)Math.round(.12 * FS); // 13
    /***
     * The heart rate interval. The algorithm is based on there being only one
     * heart beat in this interval. Assumes maximum heart rate is 200.
     */
    int HR_200_INTERVAL = (int) (60.0 / 200.0 * FS); // 39
    /**
     * How many seconds the domain interval is for the HRPlotter.
     */
    long HR_PLOT_DOMAIN_INTERVAL = 1 * 60000;  // 1 min
    /**
     * Number of standard deviations above mean to use to threshold.
     */
    int NUMBER_OF_STDDEV = 2;


    /**
     * Convert μV to mV.
     */
    double MICRO_TO_MILLI_VOLT = .001;

    /**
     * Convert millisecond to seconds.
     */
    double MS_TO_SEC = .001;

    /**
     * Filter coefficients for Butterworth fs=130 low_cutoff=5 high_cutoff=20
     */
    double[] A_BUTTERWORTH3 = {1.0, -4.026234474291334,
            7.118704187414651,
            -7.142612123715484, 4.314550872956459,
            -1.4837877480823038, 0.2259301306922936,};
    double[] B_BUTTERWORTH3 = {0.025966345753506013, 0.0,
            -0.07789903726051804,
            0.0, 0.07789903726051804, 0.0, -0.025966345753506013,};
    /**
     * Filter coefficients for Pan Tompkins derivative
     */
    double[] A_DERIVATIVE = {12};
    double[] B_DERIVATIVE = {25, -48, 36, -16, 3};


}
