package com.rails.mbta.commuterrail;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ListIterator;

import org.joda.time.LocalDate;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import com.rails.mbta.commuterrail.model.Line;
import com.rails.mbta.commuterrail.schedule.Route;
import com.rails.mbta.commuterrail.schedule.Trip;

public class MBTACommuterRailActivity extends Activity {
    public static final String SELECTED_LINE = "SELECTED_LINE";
    public static final String SELECTED_TRIPS = "SELECTED_TRIPS";
    public static final int OUTBOUND = 0;
    public static final int INBOUND = 1;

    public static final String MAIN_PREF_STORAGE_NAME = "mbtaCommuterRailPrefs";
    public static final String PREFERRED_LINE = "preferredLine";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final Spinner chosenLineSpinner = (Spinner) findViewById(R.id.chosenLine);
        ArrayAdapter<Line> commuterRailLinesAdapter = new ArrayAdapter<Line>(this, R.layout.train_selection_spinner,
                R.id.chosenLineText, Line.values());
        commuterRailLinesAdapter.setDropDownViewResource(R.layout.train_selection_item);
        chosenLineSpinner.setAdapter(commuterRailLinesAdapter);

        final CheckBox rememberCheckbox = (CheckBox) findViewById(R.id.rememberMySelectionCheckbox);
        Button goButton = (Button) findViewById(R.id.goButton);
        goButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int lineNumber = ((Line) chosenLineSpinner.getSelectedItem()).getLineNumber();
                SharedPreferences.Editor prefEditor = getSharedPreferences(MAIN_PREF_STORAGE_NAME, 0).edit();
                if (rememberCheckbox.isChecked()) {
                    prefEditor.putInt(PREFERRED_LINE, lineNumber);
                    prefEditor.commit();
                } else {
                    prefEditor.remove(PREFERRED_LINE);
                    prefEditor.commit();
                }
                LoadScheduleInformation scheduleLoader = new LoadScheduleInformation(MBTACommuterRailActivity.this,
                        lineNumber);
                scheduleLoader.execute("");
            }
        });

        SharedPreferences prefs = getSharedPreferences(MAIN_PREF_STORAGE_NAME, 0);
        if (prefs.contains(PREFERRED_LINE)) {
            int lineNumber = prefs.getInt(PREFERRED_LINE, 0);

            for (int i = 0; i < commuterRailLinesAdapter.getCount(); ++i) {
                if (commuterRailLinesAdapter.getItem(i).getLineNumber() == lineNumber) {
                    chosenLineSpinner.setSelection(i);
                    break;
                }
            }

            LoadScheduleInformation scheduleLoader = new LoadScheduleInformation(MBTACommuterRailActivity.this,
                    lineNumber);
            scheduleLoader.execute("");
        }
    }

    private static class LoadScheduleInformation extends AsyncTask<String, Route, Route> {
        private ProgressDialog progressDialog;
        private int selectedLine;
        private MBTACommuterRailActivity activity;

        public LoadScheduleInformation(MBTACommuterRailActivity activity, int selectedLine) {
            this.selectedLine = selectedLine;
            this.activity = activity;
        }

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(activity, "Loading", "Loading schedule information");
        }

        @Override
        protected Route doInBackground(String... params) {
            try {
                InputStream is = activity.getAssets().open("CR-" + selectedLine + "-data.ser");
                ObjectInputStream ois = new ObjectInputStream(is);

                return (Route) ois.readObject();
            } catch (IOException e) {
                Log.wtf("error", "error", e);
            } catch (ClassNotFoundException e) {
                Log.wtf("error", "error", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Route result) {
            /*
             * Limit trips to only those that match today.
             */
            LocalDate now = new LocalDate();
            int dayOfWeek = Integer.parseInt(Common.TODAY_FORMATTER.print(now));
            for (ListIterator<Trip> iter = result.trips.listIterator(); iter.hasNext();) {
                Trip trip = iter.next();
                if (!trip.service.serviceDays[dayOfWeek]) {
                    iter.remove();
                    continue;
                }
                if (trip.service.startDate.compareTo(now) > 0 || trip.service.endDate.compareTo(now) < 0) {
                    iter.remove();
                    continue;
                }
            }

            Common.trips = result.trips.toArray(new Trip[result.trips.size()]);

            Intent intent = new Intent(activity, ScheduleActivity.class);

            Bundle extras = new Bundle();
            extras.putInt(SELECTED_LINE, selectedLine);
            intent.putExtras(extras);

            progressDialog.dismiss();

            activity.startActivity(intent);
        }
    }
}