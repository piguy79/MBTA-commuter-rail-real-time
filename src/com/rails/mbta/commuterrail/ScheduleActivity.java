package com.rails.mbta.commuterrail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.LocalTime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.rails.mbta.commuterrail.schedule.StopTime;
import com.rails.mbta.commuterrail.schedule.Trip;
import com.rails.mbta.commuterrail.util.SharedPreferencesLibrary;

public class ScheduleActivity extends Activity {
    private static final String SCHEDULED_TIME = "scheduleTime";
    private static final String STOP_NAME = "stopName";
    private static final String ACTUAL_TIME = "actualTime";
    private static final String ACTUAL_TIME_COLOR_ID = "actualTimeColorId";
    private static final String ETA = "ETA: ";

    private static final String REAL_TIME_WARNING_SHOWN = "realTimeWarningShown";
    private static final String PREF_STORAGE_NAME = "trainSelectionPrefs";
    private static final String PREF_PREFERRED_LINES = "preferredLines";

    

    private Spinner trainSelectionSpinner;

    private ListView trainScheduleListView;
    private ImageView preferredLineImage;

    private int selectedLine;
    private Trip[] trips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null || intent.getExtras() == null || Common.trips == null) {
            Intent lineSelectionPageIntent = new Intent(this, MBTACommuterRailActivity.class);
            startActivity(lineSelectionPageIntent);
            return;
        }

        Bundle extras = intent.getExtras();
        selectedLine = extras.getInt(MBTACommuterRailActivity.SELECTED_LINE);
        trips = Common.trips;
        setContentView(R.layout.train_selection);

        trainSelectionSpinner = (Spinner) findViewById(R.id.trainNumberSpinner);
        trainScheduleListView = (ListView) findViewById(R.id.trainScheduleListView);

        ArrayAdapter<Trip> tripsAdapter = new ArrayAdapter<Trip>(ScheduleActivity.this,
                R.layout.train_schedule_spinner, R.id.chosenLineText, trips) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view;

                if (convertView == null) {
                    LayoutInflater mInflater = (LayoutInflater) ScheduleActivity.this
                            .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = mInflater.inflate(R.layout.train_schedule_spinner, parent, false);
                } else {
                    view = convertView;
                }

                TextView spinnerDepartureTimeTextView = (TextView) view.findViewById(R.id.spinnerDepartureTime);
                TextView spinnerTripInfoTextView = (TextView) view.findViewById(R.id.spinnerTripInfo);

                Object item = getItem(position);
                if (item instanceof Trip) {
                    Trip trip = (Trip) item;
                    String stopTime = "";
                    if (!trip.stopTimes.isEmpty()) {
                        stopTime = Common.TIME_FORMATTER.print(trip.stopTimes.get(0).departureTime);
                    }
                    spinnerDepartureTimeTextView.setText(stopTime);
                    spinnerTripInfoTextView.setText(trip.tripHeadsign);
                } else {
                    spinnerDepartureTimeTextView.setText("");
                    spinnerTripInfoTextView.setText("");
                }

                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                ViewGroup viewGroup = (ViewGroup) super.getDropDownView(position, convertView, parent);
                TextView view = (TextView) viewGroup.findViewById(R.id.chosenLineText);

                Object item = getItem(position);
                if (item instanceof Trip) {
                    Trip trip = (Trip) item;
                    if (isTripPreferred(trip)) {
                        SpannableString spanString = new SpannableString(view.getText());
                        spanString.setSpan(new StyleSpan(Typeface.BOLD), 0, spanString.length(), 0);
                        view.setText(spanString);
                    }
                }

                return viewGroup;
            }
        };
        tripsAdapter.setDropDownViewResource(R.layout.train_selection_item);
        trainSelectionSpinner.setAdapter(tripsAdapter);

        trainSelectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Spinner spinner = (Spinner) parent;
                Trip selectedTrip = (Trip) spinner.getSelectedItem();

                if (selectedTrip == null) {
                    return;
                }

                List<Map<String, String>> data = new ArrayList<Map<String, String>>();
                for (StopTime stopTime : selectedTrip.stopTimes) {
                    Map<String, String> row = new HashMap<String, String>();
                    row.put(STOP_NAME, stopTime.stop.stopName);
                    row.put(SCHEDULED_TIME, Common.TIME_FORMATTER.print(stopTime.arrivalTime));
                    row.put(ACTUAL_TIME, "");
                    row.put(ACTUAL_TIME_COLOR_ID, "");

                    data.add(row);
                }
                SimpleAdapter stopTimeAdapter = new SimpleAdapter(ScheduleActivity.this, data,
                        R.layout.train_schedule_list_item, new String[] { STOP_NAME, SCHEDULED_TIME, ACTUAL_TIME },
                        new int[] { R.id.tripStopTextView, R.id.scheduledTimeTextView, R.id.actualTimeTextView });

                trainScheduleListView.setAdapter(stopTimeAdapter);

                refreshPreferredLineImage();
            }

            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        preferredLineImage = (ImageView) findViewById(R.id.preferredLineImage);
        preferredLineImage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                togglePreferredLines();
            }
        });

    }

    /**
     * Based on the given trip, check if the user has previously marked this as
     * a preferred line. If so, show the "selected star" image. Otherwise, show
     * the "unselected star".
     */
    private void refreshPreferredLineImage() {
        if (isSelectedTripPreferred()) {
            preferredLineImage.setImageDrawable(getResources().getDrawable(R.drawable.rating_important));
        } else {
            preferredLineImage.setImageDrawable(getResources().getDrawable(R.drawable.rating_not_important));
        }
    }

    private void togglePreferredLines() {
        boolean isPreferred = isSelectedTripPreferred();

        Trip selectedTrip = (Trip) trainSelectionSpinner.getSelectedItem();

        SharedPreferences prefs = getSharedPreferences(PREF_STORAGE_NAME + selectedLine, 0);
        Set<String> preferredLines = SharedPreferencesLibrary.getCsvAsSet(prefs.getString(PREF_PREFERRED_LINES, ""));

        SharedPreferences.Editor editor = prefs.edit();

        if (isPreferred) {
            preferredLineImage.setImageDrawable(getResources().getDrawable(R.drawable.rating_not_important));
            preferredLines.remove(selectedTrip.tripId);

            Toast.makeText(this, "Your preferred trip was removed", Toast.LENGTH_SHORT).show();
        } else {
            preferredLineImage.setImageDrawable(getResources().getDrawable(R.drawable.rating_important));
            preferredLines.add(selectedTrip.tripId);

            Toast toast = Toast.makeText(this, "Your preferred trip was saved", Toast.LENGTH_SHORT);
            TextView toastText = (TextView) toast.getView().findViewById(android.R.id.message);
            toastText.setTextColor(getResources().getColor(R.color.saved_color));
            toast.show();
        }
        editor.putString(PREF_PREFERRED_LINES, SharedPreferencesLibrary.getSetAsCsv(preferredLines));
        editor.commit();
    }

    private boolean isSelectedTripPreferred() {
        Trip selectedTrip = (Trip) trainSelectionSpinner.getSelectedItem();

        return isTripPreferred(selectedTrip);
    }

    private boolean isTripPreferred(Trip trip) {
        SharedPreferences prefs = getSharedPreferences(PREF_STORAGE_NAME + selectedLine, 0);
        Set<String> preferredLines = SharedPreferencesLibrary.getCsvAsSet(prefs.getString(PREF_PREFERRED_LINES, ""));

        for (Iterator<String> iter = preferredLines.iterator(); iter.hasNext();) {
            if (trip.tripId.equals(iter.next())) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void onResume() {
        if (trips == null) {
            Intent lineSelectionPageIntent = new Intent(this, MBTACommuterRailActivity.class);
            startActivity(lineSelectionPageIntent);

            super.onResume();

            return;
        }

        /*
         * Show real-time beta message one time only to the user.
         */
        SharedPreferences prefs = getSharedPreferences(PREF_STORAGE_NAME, 0);
        boolean realTimeWarningShown = prefs.getBoolean(REAL_TIME_WARNING_SHOWN, false);

        if (!realTimeWarningShown) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(
                    "Warning: Real-time commuter rail information comes directly from the MBTA and is still in beta form. Data may be incorrect or missing.")
                    .setCancelable(false).setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        }

        /*
         * Let the user know that the current day has no trips.
         */
        if (trips.length == 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("No scheduled trips for today").setCancelable(false)
                    .setPositiveButton("Go back", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            ScheduleActivity.this.finish();
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        }

        selectClosestPreferredTrip();

        super.onResume();
    }

    private void selectClosestPreferredTrip() {
        SharedPreferences prefs = getSharedPreferences(PREF_STORAGE_NAME + selectedLine, 0);
        Set<String> preferredLines = SharedPreferencesLibrary.getCsvAsSet(prefs.getString(PREF_PREFERRED_LINES, ""));

        if (preferredLines.isEmpty()) {
            return;
        }

        int preferredLineCount = preferredLines.size();
        LocalTime now = LocalTime.now();

        for (int i = 0; i < trips.length; ++i) {
            if (preferredLineCount == 1) {
                if (preferredLines.contains(trips[i].tripId)) {
                    trainSelectionSpinner.setSelection(i);
                    return;
                }
            } else {
                /*
                 * Select first preferred trip that is greater than 1 hour ago.
                 */
                LocalTime departureTime = trips[i].stopTimes.get(0).departureTime;
                if (departureTime.isAfter(now.minusMinutes(60)) && preferredLines.contains(trips[i].tripId)) {
                    trainSelectionSpinner.setSelection(i);
                    return;
                }
            }
        }

        /*
         * If none found, select the first match regardless of time.
         */
        for (int i = 0; i < trips.length; ++i) {
            if (preferredLines.contains(trips[i].tripId)) {
                trainSelectionSpinner.setSelection(i);
                return;
            }
        }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            SharedPreferences.Editor prefEditor = getSharedPreferences(MBTACommuterRailActivity.MAIN_PREF_STORAGE_NAME,
                    0).edit();
            prefEditor.remove(MBTACommuterRailActivity.PREFERRED_LINE);
            prefEditor.commit();
        }
        return super.onKeyDown(keyCode, event);
    }

}
