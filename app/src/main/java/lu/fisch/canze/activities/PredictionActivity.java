package lu.fisch.canze.activities;

import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.widget.TextView;

import java.util.ArrayList;

import lu.fisch.canze.R;
import lu.fisch.canze.actors.Battery;
import lu.fisch.canze.actors.Field;
import lu.fisch.canze.interfaces.FieldListener;

public class PredictionActivity extends CanzeActivity implements FieldListener {

    private static final boolean debug = false; // set true for emulation

    public static final String SID_AvChargingPower = "427.40";
    public static final String SID_UserSoC = "42e.0";          // user SOC, not raw
    public static final String SID_Preamble_CompartmentTemperatures = "7bb.6104."; // (LBC)
    public static final String SID_RangeEstimate = "654.42";
    public static final String SID_ChargingStatusDisplay = "65b.41";


    private Battery battery;

    private double car_soc = 5;
    private double car_bat_temp = 10;
    private double car_bat_temp_ar[] = {0, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15};
    private double car_charger_ac_power = 22;
    private int car_status = 0;
    private int charging_status = 0;
    private int seconds_per_tick = 1;
    private double car_range_est = 1;

    private ArrayList<Field> subscribedFields;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prediction);

        // initialize the battery model
        battery = new Battery();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // debugging
        if (debug) {
            updatePrediction("textDebug", "Emulation");
            final Handler h = new Handler();
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    car_status = 0x1f;
                    car_bat_temp = 20;
                    car_soc = 10;
                    car_charger_ac_power = 43;
                    car_range_est = 14;
                    charging_status = 0;
                    runPrediction();
                    car_status = 0;
                    h.postDelayed(this, 10000);
                }
            }, 1);
        } else {
            initListeners();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
            removeListeners();
    }

    private void initListeners() {

        subscribedFields = new ArrayList<>();

        addListener(SID_RangeEstimate, 10000);
        addListener(SID_AvChargingPower, 10000);
        addListener(SID_UserSoC, 10000);
        addListener(SID_ChargingStatusDisplay, 10000);
        // Battery compartment temperatures
        int lastCell = (MainActivity.car == MainActivity.CAR_ZOE) ? 296 : 104;
        for (int i = 32; i <= lastCell; i += 24) {
            String sid = SID_Preamble_CompartmentTemperatures + i;
            addListener(sid, 10000);
        }
    }


    private void removeListeners() {
        // empty the query loop
        MainActivity.device.clearFields();
        // free up the listeners again
        for (Field field : subscribedFields) {
            field.removeListener(this);
        }
        subscribedFields.clear();
    }

    private void addListener(String sid, int intervalMs) {
        Field field;
        field = MainActivity.fields.getBySID(sid);
        if (field != null) {
            // activate callback to this object when a value is updated
            field.addListener(this);
            // add querying this field in the queryloop
            MainActivity.device.addActivityField(field, intervalMs);
            subscribedFields.add(field);
        } else {
            MainActivity.toast("sid " + sid + " does not exist in class Fields");
        }
    }

    // This is the event fired as soon as this the registered fields are
    // getting updated by the corresponding reader class.
    @Override
    public void onFieldUpdateEvent(final Field field) {
        String fieldId = field.getSID();
        Double fieldVal = field.getValue();

        if (fieldVal.isNaN()) return;
        // get the text field
        switch (fieldId) {

            case SID_AvChargingPower:
                car_charger_ac_power = fieldVal;
                car_status |= 0x01;
                break;
            case SID_UserSoC:
                car_soc = fieldVal;
                car_status |= 0x02;
                break;
            case SID_Preamble_CompartmentTemperatures + "32":
                car_bat_temp_ar[1] = fieldVal;
                break;
            case SID_Preamble_CompartmentTemperatures + "56":
                car_bat_temp_ar[2] = fieldVal;
                break;
            case SID_Preamble_CompartmentTemperatures + "80":
                car_bat_temp_ar[3] = fieldVal;
                break;
            case SID_Preamble_CompartmentTemperatures + "104":
                car_bat_temp_ar[4] = fieldVal;
                // set temp to valid when the last module temperature is in
                if (MainActivity.car != MainActivity.CAR_ZOE) {
                    car_bat_temp = 0;
                    for (int temp_index = 4; temp_index > 0; temp_index--) {
                        car_bat_temp += car_bat_temp_ar[temp_index];
                    }
                    car_bat_temp /= 4;
                    car_status |= 0x04;
                }
                break;
            case SID_Preamble_CompartmentTemperatures + "128":
                car_bat_temp_ar[5] = fieldVal;
                break;
            case SID_Preamble_CompartmentTemperatures + "152":
                car_bat_temp_ar[6] = fieldVal;
                break;
            case SID_Preamble_CompartmentTemperatures + "176":
                car_bat_temp_ar[7] = fieldVal;
                break;
            case SID_Preamble_CompartmentTemperatures + "200":
                car_bat_temp_ar[8] = fieldVal;
                break;
            case SID_Preamble_CompartmentTemperatures + "224":
                car_bat_temp_ar[9] = fieldVal;
                break;
            case SID_Preamble_CompartmentTemperatures + "248":
                car_bat_temp_ar[10] = fieldVal;
                break;
            case SID_Preamble_CompartmentTemperatures + "272":
                car_bat_temp_ar[11] = fieldVal;
                break;
            case SID_Preamble_CompartmentTemperatures + "296":
                car_bat_temp_ar[12] = fieldVal;
                car_bat_temp = 0;
                for (int temp_index = 12; temp_index > 0; temp_index--) {
                    car_bat_temp += car_bat_temp_ar[temp_index];
                }
                car_bat_temp /= 12;
                car_status |= 0x04;
                break;
            case SID_RangeEstimate:
                car_range_est = fieldVal;
                car_status |= 0x08;
                break;
            case SID_ChargingStatusDisplay:
                charging_status = (fieldVal == 3) ? 1 : 0;
                    car_status |= 0x10;
        }
        // display the debug values
        updatePrediction("textDebug", fieldId + ", value:" + fieldVal + ", status:" + car_status);
        if (car_status == 0x1f) {
            runPrediction();
            car_status = 0;
        }
    }

    private void runPrediction() {

        // set the battery object to an initial state equal to the real battery (
        battery.setTimeRunning(0);

        // set the internal battery temperature
        updatePrediction("texttemp", "" + (int) car_bat_temp + "°C");
        battery.setTemperature(car_bat_temp);

        // set the internal state of charge
        updatePrediction("textsoc", (int) car_soc + "%");
        battery.setStateOfChargePerc(car_soc);

        if (charging_status == 0) {
            updatePrediction("textacpwr", "Not charging");
            for (int t = 10; t <= 100; t = t + 10) {
                updatePrediction("textTIM" + t, "00:00");
                updatePrediction("textSOC" + t, "-");
                updatePrediction("textRAN" + t, "-");
                updatePrediction("textPWR" + t, "-");
            }
            return;
        }

        // set the external maximum charger capacity
        updatePrediction("textacpwr", ((int) (car_charger_ac_power * 10)) / 10 + " kW");
        battery.setChargerPower(car_charger_ac_power);

        if (car_charger_ac_power > 17) {
            //seconds_per_tick = 60; // 100 minutes = 1:40
            seconds_per_tick = 36; // 60 minutes = 1:00
        } else if (car_charger_ac_power > 5) {
            //seconds_per_tick = 120; // 200 minutes = 3:20
            seconds_per_tick = 108; // 180 minutes = 3:00
        } else {
            //seconds_per_tick = 300; // 500 minutes = 8:20
            seconds_per_tick = 270; // 450 minutes = 7:30
        }

        // now start iterating over time
        for (int t = 1; t <= 100; t++) { // 100 ticks
            battery.iterateCharging(seconds_per_tick);

            // optimization
            if ((t % 10) == 0) {
                double soc = battery.getStateOfChargePerc();
                updatePrediction("textTIM" + t, "" + formatTime(battery.getTimeRunning()));
                updatePrediction("textSOC" + t, "" + ((int) soc));
                updatePrediction("textRAN" + t, "" + ((int) (car_range_est * soc / car_soc)));
                updatePrediction("textPWR" + t, "" + ((int) battery.getDcPower()));
            }
        }
    }

    public void updatePrediction(final String id, final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv;
                tv = (TextView) findViewById(getResources().getIdentifier(id, "id", getPackageName()));
                if (tv != null) {
                    tv.setText(msg);
                }
            }
        });
    }

    private String formatTime(int t) {
        // t is in seconds
        t /= 60;
        // t is in minutes
        return "" + format2Digit(t / 60) + ":" + format2Digit(t % 60);
    }

    private String format2Digit(int t) {
        return ("00" + t).substring(t > 9 ? 2 : 1);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_empty, menu);
        return true;
    }

}
