package net.kenevans.polar.polarecg;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * Class to create an ECG plot. Note that the units used are the size of a small
 * box. These are scaled to the desired page size.
 * <p>
 * Created on Jun 16, 2019
 * By Kenneth Evans, Jr.
 */
class EcgImage {
    private static final int WIDTH = 2550;
    private static final int HEIGHT = 3300;
    private static final int GRAPH_WIDTH = 40 * 5;
    private static final int GRAPH_HEIGHT = 48 * 5;
    private static final int GRAPH_X = 8;
    private static final int GRAPH_Y = 31;
    private static final float SCALE = 11.8f;
    private static final int MINOR_COLOR = 0xffd1d1d1;
    private static final int MAJOR_COLOR = 0xff8c8c8c;
    private static final int BLOCK_COLOR = 0xff333333;
    private static final int OUTLINE_COLOR = 0xff000000;
    private static final int CURVE_COLOR = 0xff000000;
    private static final float MINOR_WIDTH = 1f;
    private static final float MAJOR_WIDTH = 2f;
    private static final float BLOCK_WIDTH = 3f;
    private static final float OUTLINE_WIDTH = 5f;
    private static final float CURVE_WIDTH = 3f;

    static Bitmap createImage(double samplingRate,
                              Bitmap logo,
                              String patientName,
                              String date,
                              String id,
                              String firmware,
                              String batteryLevel,
                              String notes,
                              String devhr,
                              String calchr,
                              String nPeaks,
                              String duration,
                              double[] ecgvals,
                              boolean[] peakvals) {
        // Graphics
        Bitmap bm = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        bm.eraseColor(Color.WHITE);
        Canvas canvas = new Canvas(bm);

        // General use, parameters will change
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setTextSize(36);

        // For QRS marks, needs to be used at same time as paint
        Paint paint1 = new Paint();
        paint1.setColor(Color.BLACK);
        paint1.setStrokeWidth(OUTLINE_WIDTH);

        // Fonts
        Typeface font = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
        Typeface fontBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        Typeface fontInfo = Typeface.create(Typeface.SANS_SERIF,
                Typeface.NORMAL);
        Typeface fontLogo = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

        // Headers
        paint.setTypeface(fontBold);
        canvas.drawText("Patient:", 100, 120, paint);
        paint.setTypeface(font);
        canvas.drawText(patientName, 300, 120, paint);

        paint.setTypeface(fontBold);
        canvas.drawText("Notes:", 850, 120, paint);
        paint.setTypeface(font);
        canvas.drawText(notes, 1025, 120, paint);

        paint.setTypeface(fontBold);
        canvas.drawText("Recorded:", 100, 165, paint);
        paint.setTypeface(font);
        canvas.drawText(date, 300, 165, paint);

        paint.setTypeface(fontBold);
        canvas.drawText("Duration:", 100, 210, paint);
        paint.setTypeface(font);
        canvas.drawText(duration, 300, 210, paint);

        paint.setTypeface(fontBold);
        canvas.drawText("Device ID:", 100, 255, paint);
        paint.setTypeface(font);
        canvas.drawText(id, 300, 255, paint);

        paint.setTypeface(fontBold);
        canvas.drawText("Battery:", 850, 255, paint);
        paint.setTypeface(font);
        canvas.drawText(batteryLevel, 1025, 255, paint);

        paint.setTypeface(fontBold);
        canvas.drawText("Firmware:", 500, 255, paint);
        paint.setTypeface(font);
        canvas.drawText(firmware, 700, 255, paint);

        paint.setTypeface(fontBold);
        canvas.drawText("Device HR:", 100, 300, paint);
        paint.setTypeface(font);
        canvas.drawText(devhr, 300, 300, paint);

        paint.setTypeface(fontBold);
        canvas.drawText("Calc HR:", 500, 300, paint);
        paint.setTypeface(font);
        canvas.drawText(calchr, 700, 300, paint);

        paint.setTypeface(fontBold);
        canvas.drawText("Peaks:", 850, 300, paint);
        paint.setTypeface(font);
        canvas.drawText(nPeaks, 1025, 300, paint);

        String scale = "Scale: 25 mm/s, 10 mm/mV ";
        paint.setTypeface(fontInfo);
        paint.setTextSize(30);
        canvas.drawText(scale, 2075, 350, paint);

        // Do the icon
        Bitmap scaledLogo = Bitmap.createScaledBitmap(logo, 100, 100, true);
        canvas.drawBitmap(scaledLogo, 2050, 116, null);
        paint.setTypeface(fontLogo);
        paint.setTextSize(48);
        paint.setColor(0xffd30024);
        canvas.drawText("KE.Net ECG", 2170, 180, paint);

        // Draw the small grid lines
        paint.setStrokeWidth(MINOR_WIDTH);
        paint.setColor(MINOR_COLOR);

        paint.setColor(MINOR_COLOR);
        for (int i = 0; i < GRAPH_WIDTH; i++) {
            drawScaled(canvas, GRAPH_X + i, GRAPH_Y, GRAPH_X + i,
                    GRAPH_Y + GRAPH_HEIGHT, paint);
        }
        for (int i = 0; i < GRAPH_HEIGHT; i++) {
            drawScaled(canvas, GRAPH_X, GRAPH_Y + i, GRAPH_X + GRAPH_WIDTH,
                    GRAPH_Y + i, paint);
        }

        // Draw the large grid lines
        paint.setStrokeWidth(MAJOR_WIDTH);
        paint.setColor(MAJOR_COLOR);
        for (int i = 0; i < GRAPH_WIDTH; i += 5) {
            drawScaled(canvas, GRAPH_X + i, GRAPH_Y, GRAPH_X + i,
                    GRAPH_Y + GRAPH_HEIGHT, paint);
        }
        for (int i = 0; i < GRAPH_HEIGHT; i += 5) {
            drawScaled(canvas, GRAPH_X, GRAPH_Y + i, GRAPH_X + GRAPH_WIDTH,
                    GRAPH_Y + i, paint);
        }

        // Draw the block grid lines
        paint.setStrokeWidth(BLOCK_WIDTH);
        paint.setColor(BLOCK_COLOR);
        for (int i = 0; i < GRAPH_WIDTH; i += 25) {
            drawScaled(canvas, GRAPH_X + i, GRAPH_Y, GRAPH_X + i,
                    GRAPH_Y + GRAPH_HEIGHT, paint);
        }
        for (int i = 0; i < GRAPH_HEIGHT; i += 60) {
            drawScaled(canvas, GRAPH_X, GRAPH_Y + i, GRAPH_X + GRAPH_WIDTH,
                    GRAPH_Y + i, paint);
        }

        // Draw the outline
        paint.setStrokeWidth(OUTLINE_WIDTH);
        paint.setColor(OUTLINE_COLOR);
        drawScaled(canvas, GRAPH_X, GRAPH_Y, GRAPH_X + GRAPH_WIDTH, GRAPH_Y,
                paint);
        drawScaled(canvas, GRAPH_X, GRAPH_Y + GRAPH_HEIGHT,
                GRAPH_X + GRAPH_WIDTH,
                GRAPH_Y + GRAPH_HEIGHT, paint);
        drawScaled(canvas, GRAPH_X, GRAPH_Y, GRAPH_X, GRAPH_Y + GRAPH_HEIGHT,
                paint);
        drawScaled(canvas, GRAPH_X + GRAPH_WIDTH, GRAPH_Y,
                GRAPH_X + GRAPH_WIDTH,
                GRAPH_Y + GRAPH_HEIGHT, paint);

        // Draw the curves
        paint.setStrokeWidth(CURVE_WIDTH);
        paint.setColor(CURVE_COLOR);
        int index = 0;
        float y0 = 0, y;
        float x0 = 0, x;
        float offsetX = GRAPH_X;
        float offsetY = GRAPH_Y + 30;
        float valueStep = 200.f / ((float) samplingRate * 8);
        for (double val : ecgvals) {
            x = index * valueStep;
            y = (float) (-10 * val);
            if (index == 0) {
                x0 = x;
                y0 = y;
                index++;
                continue;
            } else if (index == 8 * samplingRate) {
                offsetX -= (8 * samplingRate) * valueStep;
                offsetY += 60;
            } else if (index == 16 * samplingRate) {
                offsetX -= (8 * samplingRate) * valueStep;
                offsetY += 60;
            } else if (index == 24 * samplingRate) {
                offsetX -= (8 * samplingRate) * valueStep;
                offsetY += 60;
            } else if (index > 32 * samplingRate) {
                // Handle writing to the next page
                break;
            }
            drawScaled(canvas, x0 + offsetX, y0 + offsetY, x + offsetX,
                    y + offsetY, paint);
            // QRS Marks
            if (peakvals != null && peakvals[index]) {
                drawScaled(canvas,
                        x + offsetX, offsetY + 28,
                        x + offsetX, offsetY + 30,
                        paint1);
            }
            y0 = y;
            x0 = x;
            index++;
        }

        return bm;
    }


    private static void drawScaled(Canvas canvas, float x0, float y0,
                                   float x1, float y1, Paint paint) {
        float xx0 = SCALE * x0;
        float xx1 = SCALE * x1;
        float yy0 = SCALE * y0;
        float yy1 = SCALE * y1;
        canvas.drawLine(xx0, yy0, xx1, yy1, paint);

    }
}