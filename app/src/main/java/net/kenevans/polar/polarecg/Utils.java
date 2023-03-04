//Copyright (c) 2011 Kenneth Evans
//
//Permission is hereby granted, free of charge, to any person obtaining
//a copy of this software and associated documentation files (the
//"Software"), to deal in the Software without restriction, including
//without limitation the rights to use, copy, modify, merge, publish,
//distribute, sublicense, and/or sell copies of the Software, and to
//permit persons to whom the Software is furnished to do so, subject to
//the following conditions:
//
//The above copyright notice and this permission notice shall be included
//in all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
//EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
//MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
//IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
//CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
//TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
//SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package net.kenevans.polar.polarecg;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;
import android.view.ContextThemeWrapper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Map;

public class Utils implements IConstants {
    /**
     * General alert dialog.
     *
     * @param context The context.
     * @param title   The dialog title.
     * @param msg     The dialog message.
     */
    @SuppressWarnings("unused")
    private static void alert(Context context, String title, String msg) {
        try {
            AlertDialog alertDialog =
                    new AlertDialog.Builder(new ContextThemeWrapper(context,
                            R.style.PolarTheme))
                            .setTitle(title)
                            .setMessage(msg)
                            .setPositiveButton(context.getText(R.string.ok),
                                    (dialog, which) -> dialog.cancel()).create();
            alertDialog.show();
        } catch (Throwable t) {
            Log.e(getContextTag(context), "Error using " + title
                    + "AlertDialog\n" + t + "\n" + t.getMessage());
        }
    }

    /**
     * Error message dialog.
     *
     * @param context The context.
     * @param msg     The dialog message.
     */
    @SuppressWarnings("unused")
    static void errMsg(Context context, String msg) {
        Log.e(TAG, getContextTag(context) + msg);
        alert(context, context.getText(R.string.error).toString(), msg);
    }

    /**
     * Error message dialog.
     *
     * @param context The context.
     * @param msg     The dialog message.
     */
    @SuppressWarnings("unused")
    public static void warnMsg(Context context, String msg) {
        Log.w(TAG, getContextTag(context) + msg);
        alert(context, context.getText(R.string.warning).toString(), msg);
    }

    /**
     * Info message dialog.
     *
     * @param context The context.
     * @param msg     The dialog message.
     */
    @SuppressWarnings("unused")
    static void infoMsg(Context context, String msg) {
        Log.i(TAG, getContextTag(context) + msg);
        alert(context, context.getText(R.string.info).toString(), msg);
    }

    /**
     * Exception message dialog. Displays message plus the exception and
     * exception message.
     *
     * @param context The context.
     * @param msg     The dialog message.
     * @param t       The throwable.
     */
    @SuppressWarnings("unused")
    static void excMsg(Context context, String msg, Throwable t) {
        String fullMsg = msg += "\n"
                + context.getText(R.string.exception) + ": " + t
                + "\n" + t.getMessage();
        Log.e(TAG, getContextTag(context) + msg);
        alert(context, context.getText(R.string.exception).toString(), fullMsg);
    }

    /**
     * Utility method to get a tag representing the Context to associate with a
     * log message.
     *
     * @param context The context.
     * @return The context tag.
     */
    @SuppressWarnings("unused")
    private static String getContextTag(Context context) {
        if (context == null) {
            return "<???>: ";
        }
        return "Utils: " + context.getClass().getSimpleName() + ": ";
    }

    /**
     * Get the stack trace for a throwable as a String.
     *
     * @param t The throwable.
     * @return The stack trace as a String.
     */
    @SuppressWarnings("unused")
    public static String getStackTraceString(Throwable t) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        t.printStackTrace(ps);
        ps.close();
        return baos.toString();
    }

    /**
     * Get the extension of a file.
     *
     * @param file The file.
     * @return The extension without the dot.
     */
    @SuppressWarnings("unused")
    public static String getExtension(File file) {
        String ext = null;
        String s = file.getName();
        int i = s.lastIndexOf('.');
        if (i > 0 && i < s.length() - 1) {
            ext = s.substring(i + 1).toLowerCase();
        }
        return ext;
    }

    /**
     * Utility method for printing a hash code in hex.
     *
     * @param obj The object whose hash code is desired.
     * @return The hex-formatted hash code.
     */
    @SuppressWarnings("unused")
    public static String getHashCode(Object obj) {
        if (obj == null) {
            return "null";
        }
        return String.format("%08X", obj.hashCode());
    }

    /**
     * Get the version name for the application with the specified context.
     *
     * @param context The context.
     * @return The package name.
     */
    @SuppressWarnings("unused")
    public static String getVersion(Context context) {
        String versionName = "NA";
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                versionName = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(),
                                PackageManager.PackageInfoFlags.of(0)).versionName;
            } else {
                versionName = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0).versionName;
            }
        } catch (Exception ex) {
            // Do nothing
        }
        return versionName;
    }

    /**
     * Get the orientation of the device.
     *
     * @param ctx The Context.
     * @return Either "Portrait" or "Landscape".
     */
    @SuppressWarnings("unused")
    public static String getOrientation(Context ctx) {
        int orientation = ctx.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return "Portrait";
        } else {
            return "Landscape";
        }
    }

    /**
     * Utility method to get an info string listing all the keys,value pairs
     * in the given SharedPreferences.
     *
     * @param prefix String with text to prepend to each line, e.g. "    ".
     * @param prefs  The given Preferences.
     * @return The info/
     */
    @SuppressWarnings("unused")
    public static String getSharedPreferencesInfo(String prefix,
                                                  SharedPreferences prefs) {
        Map<String, ?> map = prefs.getAll();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            sb.append(prefix).append("key=").append(key)
                    .append(" value=").append(value).append("\n");
        }
        return sb.toString();
    }
}
