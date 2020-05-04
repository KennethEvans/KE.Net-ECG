package net.kenevans.polar.polarecg;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.util.LinkedList;

/*
 * Created on Jun 16, 2019
 * By Kenneth Evans, Jr.
 */

class EcgImage {
    private static final String DEST_DIR = "C:/Scratch/ECG/Polar ECG/Images";
    private static final String SRC_DIR = "C:/Scratch/ECG/Polar ECG/CSV Files";
    private static final int WIDTH = 2550;
    private static final int HEIGHT = 3300;
    private static final int GRAPH_WIDTH = 40 * 5;
    private static final int GRAPH_HEIGHT = 48 * 5;
    private static final int GRAPH_X = 8;
    private static final int GRAPH_Y = 31;
    private static final float SCALE = 11.8f;
    private static final String IMAGE_TYPE = "png";
    private static int MINOR_COLOR = 0xffd1d1d1;
    private static int MAJOR_COLOR = 0xff8c8c8c;
    private static int BLOCK_COLOR = 0xff333333;
    private static int OUTLINE_COLOR = 0xff000000;
    private static int CURVE_COLOR = 0xff000000;
    private static float MINOR_WIDTH = 1f;
    private static float MAJOR_WIDTH = 2f;
    private static float BLOCK_WIDTH = 3f;
    private static float OUTLINE_WIDTH = 5f;
    private static float CURVE_WIDTH = 3f;

    static Bitmap createImage(Context context, String date, String id,
                              String firmware,
                              String batteryLevel,
                              String notes, String hr, String duration,
                              LinkedList<Number> vals) {
        Bitmap bm = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        bm.eraseColor(Color.WHITE);
        Canvas canvas = new Canvas(bm);
        Paint paint = new Paint();
        Typeface font = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
        Typeface fontBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        Typeface fontInfo = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
        Typeface fontLogo = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

        paint.setColor(Color.BLACK);
        paint.setTextSize(36);

        paint.setTypeface(fontBold);
        canvas.drawText("Patient:", 100, 143, paint);

        paint.setTypeface(fontBold);
        canvas.drawText("Recorded:", 100, 188, paint);
        paint.setTypeface(font);
        canvas.drawText(date, 300, 188, paint);

        paint.setTypeface(fontBold);
        canvas.drawText("Device ID:", 100, 278, paint);
        paint.setTypeface(font);
        canvas.drawText(id, 300, 278, paint);

        paint.setTypeface(fontBold);
        canvas.drawText("Battery:", 850, 278, paint);
        paint.setTypeface(font);
        canvas.drawText(batteryLevel, 1025, 278, paint);

        paint.setTypeface(fontBold);
        canvas.drawText("Firmware:", 500, 278, paint);
        paint.setTypeface(font);
        canvas.drawText(firmware, 700, 278, paint);

        // The current line should be notes
        paint.setTypeface(fontBold);
        canvas.drawText("Notes:", 850, 143, paint);
        paint.setTypeface(font);
        canvas.drawText(notes, 1025, 143, paint);

        paint.setTypeface(fontBold);
        canvas.drawText("Heart Rate:", 100, 233, paint);
        paint.setTypeface(font);
        canvas.drawText(hr, 300, 233, paint);

        paint.setTypeface(fontBold);
        canvas.drawText("Duration:", 500, 232, paint);
        paint.setTypeface(font);
        canvas.drawText(duration, 700, 232, paint);

        String scale = "Scale: 25 mm/s, 10 mm/mV ";
        paint.setTypeface(fontInfo);
        paint.setTextSize(30);
        canvas.drawText(scale, 2075, 350, paint);

        // Do the icon
        Bitmap logo = BitmapFactory.decodeResource(context.getResources(),
                R.drawable.polar_ecg);
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
        float valueStep = 1.f / (130.f * .04f);
        int nSamples = vals.size();
        for (Number val : vals) {
            x = index * valueStep;
            y = -10.f * val.floatValue();
            if (index == 0) {
                x0 = x;
                y0 = y;
                index++;
                continue;
            } else if (index == 1040) {
                offsetX -= 1040 * valueStep;
                offsetY += 60;
            } else if (index == 2080) {
                offsetX -= 1040 * valueStep;
                offsetY += 60;
            } else if (index == 3120) {
                offsetX -= 1040 * valueStep;
                offsetY += 60;
            } else if (index > 4160) {
                // Handle writing to the next page
                break;
            }
            drawScaled(canvas, x0 + offsetX, y0 + offsetY, x + offsetX,
                    y + offsetY, paint);
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

/*    private static void processFile(File file, BufferedImage bi)
            throws Exception {
        if (file == null) {
            System.out.println("processFile: file is null");
            return;
        }
        if (!file.exists()) {
            System.out
                    .println("processFile: Does not exist: " + file.getPath());
            return;
        }
        Graphics2D g2d = bi.createGraphics();
        g2d.setPaint(Color.BLACK);
        Font font = new Font("TimesRoman", Font.PLAIN, 36);
        Font fontBold = new Font("TimesRoman", Font.BOLD, 36);
        Font fontInfo = new Font("TimesRoman", Font.PLAIN, 30);
        Font fontLogo = new Font("Helvetica", Font.BOLD, 48);

        BufferedReader in = null;
        in = new BufferedReader(new FileReader(file));
        String line = "";

        g2d.setFont(fontBold);
        g2d.drawString("Patient:", 100, 143);

        String date = in.readLine();
        g2d.setFont(fontBold);
        g2d.drawString("Recorded:", 100, 188);
        g2d.setFont(font);
        g2d.drawString(date, 300, 188);

        // Read lines that may not be there
        boolean repeat = true;
        while (repeat) {
            line = in.readLine();
            repeat = false;
            if (line.startsWith("ID")) {
                repeat = true;
                g2d.setFont(fontBold);
                g2d.drawString("Device ID:", 100, 278);
                g2d.setFont(font);
                g2d.drawString(line.substring(5), 300, 278);
            } else if (line.startsWith("Battery")) {
                g2d.setFont(fontBold);
                g2d.drawString("Battery:", 850, 278);
                g2d.setFont(font);
                g2d.drawString(line.substring(15), 1025, 278);
                repeat = true;
            } else if (line.startsWith("Firmware")) {
                g2d.setFont(fontBold);
                g2d.drawString("Firmware:", 500, 278);
                g2d.setFont(font);
                g2d.drawString(line.substring(10), 700, 278);
                repeat = true;
            } else if (line.startsWith("Firmware")) {
                repeat = true;
            }
        }

        // The current line should be notes
        String notes = line;
        g2d.setFont(fontBold);
        g2d.drawString("Notes:", 850, 143);
        g2d.setFont(font);
        g2d.drawString(notes, 1025, 143);

        String hr = in.readLine().substring(3);
        g2d.setFont(fontBold);
        g2d.drawString("Heart Rate:", 100, 233);
        g2d.setFont(font);
        g2d.drawString(hr, 300, 233);

        String[] tokens = in.readLine().split(" ");
        int values = Integer.parseInt(tokens[0]);
        // Assuming duration is in seconds
        double duration = Double.parseDouble(tokens[2]);
        double valueStep = duration / values / .04;
        String durationString = duration + " " + tokens[3];
        g2d.setFont(fontBold);
        g2d.drawString("Duration:", 500, 232);
        g2d.setFont(font);
        g2d.drawString(durationString, 700, 232);

        String scale = "Scale: 25 mm/s, 10 mm/mV ";
        g2d.setFont(fontInfo);
        g2d.drawString(scale, 2117, 350);

        // Do the icon
        BufferedImage image = ImageIO.read(EcgImage.class.getClassLoader()
                .getResource("resources/polar_ecg.png"));
        g2d.drawImage(image, 2050, 116, null);
        g2d.setFont(fontLogo);
        g2d.setPaint(new Color(211, 0, 36));
        g2d.drawString("KE.Net ECG", 2170, 180);

        // Draw the small curves
        AffineTransform scalingTransform = AffineTransform
                .getScaleInstance(SCALE, SCALE);
        g2d.setPaint(Color.BLACK);
        g2d.transform(scalingTransform);
        g2d.setStroke(new BasicStroke(CURVE_WIDTH));
        g2d.setPaint(new Color(CURVE_COLOR, CURVE_COLOR, CURVE_COLOR));
        int index = 0;
        double y0 = 0, y;
        double x0 = 0, x;
        double offsetX = GRAPH_X;
        double offsetY = GRAPH_Y + 30;
        Line2D line2d = new Line2D.Double();
        while ((line = in.readLine()) != null) {
            x = index * valueStep;
            y = -10. * Double.parseDouble(line);
            if (index == 0) {
                x0 = x;
                y0 = y;
                index++;
                continue;
            } else if (index == 1040) {
                offsetX -= 1040 * valueStep;
                offsetY += 60;
            } else if (index == 2080) {
                offsetX -= 1040 * valueStep;
                offsetY += 60;
            } else if (index == 3120) {
                offsetX -= 1040 * valueStep;
                offsetY += 60;
            } else if (index > 4160) {
                // Handle writing to the next page
                break;
            }
            line2d.setLine(x0 + offsetX, y0 + offsetY, x + offsetX,
                    y + offsetY);
            // System.out.println((x0 + offsetX) + " " + (y0 + offsetY) + " "
            // + (x + offsetX) + " " + (y + offsetY));
            g2d.draw(line2d);
            y0 = y;
            x0 = x;
            index++;
        }

        // Cleanup
        in.close();
        in = null;
        System.out.println("Processed " + file.getPath());
    }
*/
}