package net.kenevans.polar.polarhrcompare;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private String TAG = "Polar_MainActivity";
    private String sharedPrefsKey1 = "polar_device_id_1";
    private String sharedPrefsKey2 = "polar_device_id_2";
    private String DEVICE_ID_1, DEVICE_ID_2;
    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPreferences = this.getPreferences(Context.MODE_PRIVATE);
        checkBT();
    }

    public void onClickConnect(View view) {
        checkBT();
        DEVICE_ID_1 = sharedPreferences.getString(sharedPrefsKey1, "");
        DEVICE_ID_2 = sharedPreferences.getString(sharedPrefsKey2, "");
        Log.d(TAG,
                "DEVICE_ID_1=" + DEVICE_ID_1 + " DEVICE_ID_2=" + DEVICE_ID_2);
        if (DEVICE_ID_1.equals("")) {
            showDialog1(view);
        }
        if (DEVICE_ID_2.equals("")) {
            showDialog2(view);
        }
        Toast.makeText(this,
                getString(R.string.connecting) + " " + DEVICE_ID_1 + "\n"
                        + getString(R.string.connecting) + " " + DEVICE_ID_2,
                Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, HRActivity.class);
        intent.putExtra("id1", DEVICE_ID_1);
        intent.putExtra("id2", DEVICE_ID_2);
        startActivity(intent);
    }

    public void onClickChangeID1(View view) {
        showDialog1(view);
    }

    public void onClickChangeID2(View view) {
        showDialog2(view);
    }

    public void showDialog1(View view) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this,
                R.style.PolarTheme);
        dialog.setTitle("Enter device 1 ID");

        View viewInflated =
                LayoutInflater.from(getApplicationContext()).
                        inflate(R.layout.device_id_dialog_layout,
                                (ViewGroup) view.getRootView(),
                                false);

        final EditText input = viewInflated.findViewById(R.id.input);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        DEVICE_ID_1 = sharedPreferences.getString(sharedPrefsKey1, "");
        input.setText(DEVICE_ID_1);
        dialog.setView(viewInflated);

        dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                DEVICE_ID_1 = input.getText().toString();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(sharedPrefsKey1, DEVICE_ID_1);
                editor.apply();
            }
        });
        dialog.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        dialog.show();
    }

    public void showDialog2(View view) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this,
                R.style.PolarTheme);
        dialog.setTitle("Enter device 2 ID");

        View viewInflated =
                LayoutInflater.from(getApplicationContext()).
                        inflate(R.layout.device_id_dialog_layout,
                                (ViewGroup) view.getRootView(),
                                false);

        final EditText input = viewInflated.findViewById(R.id.input);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        DEVICE_ID_2 = sharedPreferences.getString(sharedPrefsKey2, "");
        input.setText(DEVICE_ID_2);
        dialog.setView(viewInflated);

        dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                DEVICE_ID_2 = input.getText().toString();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(sharedPrefsKey2, DEVICE_ID_2);
                editor.apply();
            }
        });
        dialog.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        dialog.show();
    }

    public void checkBT() {
        BluetoothAdapter mBluetoothAdapter =
                BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent =
                    new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 2);
        }

        //requestPermissions() method needs to be called when the build SDK
        // version is 23 or above
        if (Build.VERSION.SDK_INT >= 23) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }
}
