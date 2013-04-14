package com.rails.mbta.commuterrail;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.joda.time.LocalTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Log;
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

import com.rails.mbta.commuterrail.model.Flag;
import com.rails.mbta.commuterrail.model.TripStop;
import com.rails.mbta.commuterrail.schedule.StopTime;
import com.rails.mbta.commuterrail.schedule.Trip;
import com.rails.mbta.commuterrail.util.SharedPreferencesLibrary;

public class TrainSelectionView extends Activity {
    private static final String SCHEDULED_TIME = "scheduleTime";
    private static final String STOP_NAME = "stopName";
    private static final String ACTUAL_TIME = "actualTime";
    private static final String ACTUAL_TIME_COLOR_ID = "actualTimeColorId";
    private static final String ETA = "ETA: ";

    private static final String REAL_TIME_WARNING_SHOWN = "realTimeWarningShown";
    private static final String PREF_STORAGE_NAME = "trainSelectionPrefs";
    private static final String PREF_PREFERRED_LINES = "preferredLines";

    private static final String LAST_UPDATE = "Last update: ";
    private static final String LAST_UPDATE_RETRIEVING = LAST_UPDATE + "Retrieving...";
    private static final long REAL_TIME_UPDATE_IN_MILLIS = 15000;
    private Handler realTimeUpdateHandler = new Handler();
    private RealTimeUpdateRunnable realTimeUpdateRunnable;

    private TextView realTimeUpdateStatusTextView;
    private Spinner trainSelectionSpinner;
    private TextView realTimeUpdateConnectionStatusTextView;
    private ListView trainScheduleListView;
    private ImageView preferredLineImage;

    private int trainDelayLateColor;
    private int trainDelayWarningColor;
    private int defaultColor;

    private int selectedDirection;
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

        trainDelayLateColor = getResources().getColor(R.color.train_delay_late);
        trainDelayWarningColor = getResources().getColor(R.color.train_delay_warning);

        Bundle extras = intent.getExtras();
        selectedLine = extras.getInt(MBTACommuterRailActivity.SELECTED_LINE);
        trips = Common.trips;
        setContentView(R.layout.train_selection);

        trainSelectionSpinner = (Spinner) findViewById(R.id.trainNumberSpinner);
        trainScheduleListView = (ListView) findViewById(R.id.trainScheduleListView);
        realTimeUpdateStatusTextView = (TextView) findViewById(R.id.realTimeUpdateStatusTextView);
        realTimeUpdateConnectionStatusTextView = (TextView) findViewById(R.id.realTimeUpdateConnectionStatusTextView);

        defaultColor = realTimeUpdateStatusTextView.getTextColors().getDefaultColor();

        ArrayAdapter<Trip> tripsAdapter = new ArrayAdapter<Trip>(TrainSelectionView.this,
                R.layout.train_schedule_spinner, R.id.chosenLineText, trips) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view;

                if (convertView == null) {
                    LayoutInflater mInflater = (LayoutInflater) TrainSelectionView.this
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
                SimpleAdapter stopTimeAdapter = new SimpleAdapter(TrainSelectionView.this, data,
                        R.layout.train_schedule_list_item, new String[] { STOP_NAME, SCHEDULED_TIME, ACTUAL_TIME },
                        new int[] { R.id.tripStopTextView, R.id.scheduledTimeTextView, R.id.actualTimeTextView }) {

                    public View getView(int position, View convertView, android.view.ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);

                        TextView actualTimeTextView = (TextView) view.findViewById(R.id.actualTimeTextView);
                        Map<String, String> rowData = (Map<String, String>) getItem(position);
                        if (rowData.get(ACTUAL_TIME_COLOR_ID).isEmpty()) {
                            actualTimeTextView.setTextColor(defaultColor);
                        } else {
                            actualTimeTextView.setTextColor(Integer.parseInt(rowData.get(ACTUAL_TIME_COLOR_ID)));
                        }
                        return view;
                    };
                };

                trainScheduleListView.setAdapter(stopTimeAdapter);

                startRealTimeUpdatePosts();
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

    private void startRealTimeUpdatePosts() {
        stopRealTimeUpdatePosts();

        realTimeUpdateStatusTextView.setText(LAST_UPDATE_RETRIEVING);
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        realTimeUpdateRunnable = new RealTimeUpdateRunnable(selectedLine, connectivityManager, trainScheduleListView,
                trainSelectionSpinner, realTimeUpdateStatusTextView, realTimeUpdateConnectionStatusTextView);
        realTimeUpdateHandler.post(realTimeUpdateRunnable);
    }

    private void stopRealTimeUpdatePosts() {
        if (realTimeUpdateRunnable != null) {
            realTimeUpdateRunnable.kill();
        }
        realTimeUpdateHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onPause() {
        stopRealTimeUpdatePosts();

        SharedPreferences.Editor prefEditor = getSharedPreferences(PREF_STORAGE_NAME, 0).edit();
        prefEditor.putBoolean(REAL_TIME_WARNING_SHOWN, true);
        prefEditor.commit();

        super.onPause();
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
                            TrainSelectionView.this.finish();
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        }

        selectClosestPreferredTrip();
        startRealTimeUpdatePosts();

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
                 * Select first preferred trip that is greater than 75 mins ago.
                 */
                LocalTime departureTime = trips[i].stopTimes.get(0).departureTime;
                if (departureTime.isAfter(now.minusMinutes(75)) && preferredLines.contains(trips[i].tripId)) {
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

    private class RealTimeUpdateRunnable implements Runnable {
        private int selectedLine;
        private ConnectivityManager connectivityManager;
        private ListView trainScheduleListView;
        private Spinner trainSelectionSpinner;
        private TextView realTimeUpdateStatusTextView;
        private TextView realTimeUpdateConnectionStatusTextView;
        private boolean shouldKill = false;

        public RealTimeUpdateRunnable(int selectedLine, ConnectivityManager connectivityManager,
                ListView trainScheduleListView, Spinner trainSelectionSpinner, TextView realTimeUpdateStatusTextView,
                TextView realTimeUpdateConnectionStatusTextView) {
            this.selectedLine = selectedLine;
            this.connectivityManager = connectivityManager;
            this.trainScheduleListView = trainScheduleListView;
            this.trainSelectionSpinner = trainSelectionSpinner;
            this.realTimeUpdateStatusTextView = realTimeUpdateStatusTextView;
            this.realTimeUpdateConnectionStatusTextView = realTimeUpdateConnectionStatusTextView;
        }

        public void kill() {
            this.shouldKill = true;
        }

        public void run() {
            if (!shouldKill) {
                new LoadLineInformation(connectivityManager, trainScheduleListView, trainSelectionSpinner,
                        realTimeUpdateStatusTextView, realTimeUpdateConnectionStatusTextView, this).execute(Integer
                        .toString(selectedLine));
            }

        }
    }

    private class LoadLineInformation extends AsyncTask<String, Void, List<TripStop>> {

        private static final String KEY = "Key";
        private static final String VALUE = "Value";
        private static final String MESSAGES = "Messages";
        private ConnectivityManager connectivityManager;
        private ListView trainScheduleListView;
        private Spinner trainSelectionSpinner;
        private TextView realTimeUpdateStatusTextView;
        private TextView realTimeUpdateConnectionStatusTextView;
        private RealTimeUpdateRunnable realTimeUpdateRunnable;

        public LoadLineInformation(ConnectivityManager connectivityManager, ListView trainScheduleListView,
                Spinner trainSelectionSpinner, TextView realTimeUpdateStatusTextView,
                TextView realTimeUpdateConnectionStatusTextView, RealTimeUpdateRunnable realTimeUpdateRunnable) {
            this.connectivityManager = connectivityManager;
            this.trainScheduleListView = trainScheduleListView;
            this.trainSelectionSpinner = trainSelectionSpinner;
            this.realTimeUpdateStatusTextView = realTimeUpdateStatusTextView;
            this.realTimeUpdateConnectionStatusTextView = realTimeUpdateConnectionStatusTextView;
            this.realTimeUpdateRunnable = realTimeUpdateRunnable;

        }

        @Override
        protected List<TripStop> doInBackground(String... params) {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo == null || !networkInfo.isConnected()) {
                return null;
            }

            List<TripStop> allTripStops = new ArrayList<TripStop>();
            try {
                URL url = new URL("http://developer.mbta.com/lib/RTCR/RailLine_" + params[0] + ".json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(30000);
                connection.connect();

                StringBuilder sb = new StringBuilder();
                InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                char[] buffer = new char[0x1000];
                int read = 0;
                while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
                    sb.append(buffer, 0, read);
                }
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                JSONArray allTrains = json.getJSONArray(MESSAGES);
                for (int i = 0; i < allTrains.length(); ++i) {
                    // Old format
                    if (allTrains.optJSONArray(i) != null) {
                        JSONArray tripStopArray = allTrains.getJSONArray(i);
                        TripStop tripStop = new TripStop();
                        for (int j = 0; j < tripStopArray.length(); ++j) {
                            JSONObject tripStopRow = tripStopArray.getJSONObject(j);
                            String key = (String) tripStopRow.get(KEY);
                            String value = (String) tripStopRow.get(VALUE);

                            tripStop.consume(key, value);
                        }
                        allTripStops.add(tripStop);
                    } else {
                        // New format
                        JSONObject jsonTripStop = allTrains.getJSONObject(i);
                        
                        TripStop tripStop = new TripStop();
                        tripStop.consume("Timestamp", jsonTripStop.getString("TimeStamp"));
                        tripStop.consume("Trip", jsonTripStop.getString("Trip"));
                        tripStop.consume("Destination", jsonTripStop.getString("Destination"));
                        tripStop.consume("Stop", jsonTripStop.getString("Stop"));
                        tripStop.consume("Scheduled", jsonTripStop.getString("Scheduled"));
                        tripStop.consume("Flag", jsonTripStop.getString("Flag"));
                        tripStop.consume("Vehicle", jsonTripStop.getString("Vehicle"));
                        tripStop.consume("Latitude", jsonTripStop.getString("Latitude"));
                        tripStop.consume("Longitude", jsonTripStop.getString("Longitude"));
                        tripStop.consume("Heading", jsonTripStop.getString("Heading"));
                        tripStop.consume("Speed", jsonTripStop.getString("Speed"));
                        tripStop.consume("Lateness", jsonTripStop.getString("Lateness"));
                        
                        allTripStops.add(tripStop);
                    }
                }

            } catch (MalformedURLException e) {
                Log.wtf("error", "error", e);
            } catch (IOException e) {
                Log.wtf("error", "error", e);
            } catch (JSONException e) {
                Log.wtf("error", "error", e);
            }

            Collections.sort(allTripStops, new Comparator<TripStop>() {
                public int compare(TripStop o1, TripStop o2) {
                    return o1.getScheduled().compareTo(o2.getScheduled());
                }
            });

            return allTripStops;
        }

        @Override
        protected void onPostExecute(List<TripStop> result) {
            Trip selectedTrip = (Trip) trainSelectionSpinner.getSelectedItem();

            if (selectedTrip == null) {
                return;
            }

            if (result == null) {
                realTimeUpdateConnectionStatusTextView.setText("No connection");
            } else {
                /*
                 * Remove information that does not belong to this trip.
                 */
                for (ListIterator<TripStop> iter = result.listIterator(); iter.hasNext();) {
                    TripStop tripStop = iter.next();
                    if (!selectedTrip.tripHeadsign.contains("(Train " + tripStop.getTrip() + ")")) {
                        iter.remove();
                    }
                }

                realTimeUpdateConnectionStatusTextView.setText("");
                SimpleAdapter adapter = (SimpleAdapter) trainScheduleListView.getAdapter();
                for (int i = 0; i < adapter.getCount(); ++i) {
                    HashMap<String, String> row = (HashMap<String, String>) adapter.getItem(i);
                    row.put(ACTUAL_TIME, "");
                    row.put(ACTUAL_TIME_COLOR_ID, "");

                    for (TripStop tripStop : result) {
                        if (tripStop.getStop().equals(row.get(STOP_NAME))) {
                            String realTimeInfo = "";
                            Flag statusFlag = tripStop.getFlag();
                            if (statusFlag == Flag.PRE || statusFlag == Flag.DEL) {
                                Integer late = tripStop.getLateness();
                                if (late == null) {
                                    realTimeInfo = statusFlag.toString();
                                } else {
                                    LocalTime scheduledTime = Common.TIME_FORMATTER.parseLocalTime(row
                                            .get(SCHEDULED_TIME));
                                    scheduledTime = scheduledTime.plusSeconds(late);
                                    realTimeInfo = ETA + Common.TIME_FORMATTER.print(scheduledTime);

                                    if (late >= 180 && late < 300) {
                                        row.put(ACTUAL_TIME_COLOR_ID, "" + trainDelayWarningColor);
                                    } else if (late >= 300) {
                                        row.put(ACTUAL_TIME_COLOR_ID, "" + trainDelayLateColor);
                                    }

                                }
                            } else {
                                realTimeInfo = statusFlag.toString();
                            }

                            row.put(ACTUAL_TIME, realTimeInfo);
                        }
                    }
                }

                adapter.notifyDataSetChanged();
                realTimeUpdateStatusTextView.setText(LAST_UPDATE
                        + Common.TIME_FORMATTER_W_SECONDS.print(new LocalTime()));
            }

            realTimeUpdateHandler.postDelayed(realTimeUpdateRunnable, REAL_TIME_UPDATE_IN_MILLIS);
        }
    }
}
