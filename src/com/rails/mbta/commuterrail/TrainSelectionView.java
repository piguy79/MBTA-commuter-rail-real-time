package com.rails.mbta.commuterrail;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.rails.mbta.commuterrail.model.Flag;
import com.rails.mbta.commuterrail.model.TripStop;
import com.rails.mbta.commuterrail.schedule.Route;
import com.rails.mbta.commuterrail.schedule.StopTime;
import com.rails.mbta.commuterrail.schedule.Trip;

public class TrainSelectionView extends Activity {
    private static final String SCHEDULED_TIME = "scheduleTime";
    private static final String STOP_NAME = "stopName";
    private static final String ACTUAL_TIME = "actualTime";
    private static final String ACTUAL_TIME_COLOR_ID = "actualTimeColorId";
    private static final String ETA = "ETA: ";

    private static final String LAST_UPDATE = "Last update: ";
    private static final String LAST_UPDATE_RETRIEVING = LAST_UPDATE + "Retrieving...";
    private static final long REAL_TIME_UPDATE_IN_MILLIS = 15000;
    private Route scheduledRoute;
    private Handler realTimeUpdateHandler = new Handler();
    private RealTimeUpdateRunnable realTimeUpdateRunnable;

    private LoadScheduleInformation scheduleLoader;

    private TextView realTimeUpdateStatusTextView;

    private int trainDelayLateColor;
    private int trainDelayWarningColor;
    private int defaultColor;

    private int selectedDirection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null || intent.getExtras() == null) {
            Intent lineSelectionPageIntent = new Intent(this, MBTACommuterRailActivity.class);
            startActivity(lineSelectionPageIntent);
            return;
        }

        trainDelayLateColor = getResources().getColor(R.color.train_delay_late);
        trainDelayWarningColor = getResources().getColor(R.color.train_delay_warning);

        Bundle extras = intent.getExtras();
        final int selectedLine = extras.getInt(MBTACommuterRailActivity.SELECTED_LINE);
        selectedDirection = extras.getInt(MBTACommuterRailActivity.SELECTED_DIRECTION);

        setContentView(R.layout.train_selection);

        Spinner trainSelectionSpinner = (Spinner) findViewById(R.id.trainNumberSpinner);
        final ListView trainScheduleListView = (ListView) findViewById(R.id.trainScheduleListView);
        realTimeUpdateStatusTextView = (TextView) findViewById(R.id.realTimeUpdateStatusTextView);
        final TextView realTimeUpdateConnectionStatusTextView = (TextView) findViewById(R.id.realTimeUpdateConnectionStatusTextView);

        defaultColor = realTimeUpdateStatusTextView.getTextColors().getDefaultColor();

        scheduleLoader = new LoadScheduleInformation(trainSelectionSpinner, selectedLine);
        scheduleLoader.execute("");

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

                realTimeUpdateStatusTextView.setText(LAST_UPDATE_RETRIEVING);
                realTimeUpdateHandler.removeCallbacks(realTimeUpdateRunnable);
                realTimeUpdateHandler.post(realTimeUpdateRunnable);
            }

            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        realTimeUpdateRunnable = new RealTimeUpdateRunnable(selectedLine, connectivityManager, trainScheduleListView,
                trainSelectionSpinner, realTimeUpdateStatusTextView, realTimeUpdateConnectionStatusTextView);
        realTimeUpdateHandler.post(realTimeUpdateRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();

        realTimeUpdateHandler.removeCallbacks(realTimeUpdateRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();

        realTimeUpdateStatusTextView.setText(LAST_UPDATE_RETRIEVING);
        realTimeUpdateHandler.removeCallbacks(realTimeUpdateRunnable);
        realTimeUpdateHandler.post(realTimeUpdateRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        /*
         * Attempt to cancel, just in case it is still running.
         */
        scheduleLoader.cancel(true);
    }

    private class RealTimeUpdateRunnable implements Runnable {
        private int selectedLine;
        private ConnectivityManager connectivityManager;
        private ListView trainScheduleListView;
        private Spinner trainSelectionSpinner;
        private TextView realTimeUpdateStatusTextView;
        private TextView realTimeUpdateConnectionStatusTextView;

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

        public void run() {
            new LoadLineInformation(connectivityManager, trainScheduleListView, trainSelectionSpinner,
                    realTimeUpdateStatusTextView, realTimeUpdateConnectionStatusTextView).execute(Integer
                    .toString(selectedLine));
        }
    }

    private class LoadScheduleInformation extends AsyncTask<String, Route, Route> {
        private ProgressDialog progressDialog;
        private Spinner trainSelection;
        private int selectedLine;

        public LoadScheduleInformation(Spinner trainSelection, int selectedLine) {
            this.trainSelection = trainSelection;
            this.selectedLine = selectedLine;
        }

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(TrainSelectionView.this, "Loading", "Loading schedule information");
        }

        @Override
        protected Route doInBackground(String... params) {
            try {
                InputStream is = getAssets().open("CR-" + selectedLine + "-data.ser");
                ObjectInputStream ois = new ObjectInputStream(is);

                TrainSelectionView.this.scheduledRoute = (Route) ois.readObject();
            } catch (IOException e) {
                Log.wtf("error", "error", e);
            } catch (ClassNotFoundException e) {
                Log.wtf("error", "error", e);
            }
            return TrainSelectionView.this.scheduledRoute;
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
                if (trip.directionId != TrainSelectionView.this.selectedDirection) {
                    iter.remove();
                    continue;
                }
            }

            Trip[] trips = result.trips.toArray(new Trip[result.trips.size()]);

            ArrayAdapter<Trip> tripsAdapter = new ArrayAdapter<Trip>(TrainSelectionView.this,
                    R.layout.train_selection_item, R.id.chosenLineText, trips);
            trainSelection.setAdapter(tripsAdapter);

            progressDialog.dismiss();
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

        public LoadLineInformation(ConnectivityManager connectivityManager, ListView trainScheduleListView,
                Spinner trainSelectionSpinner, TextView realTimeUpdateStatusTextView,
                TextView realTimeUpdateConnectionStatusTextView) {
            this.connectivityManager = connectivityManager;
            this.trainScheduleListView = trainScheduleListView;
            this.trainSelectionSpinner = trainSelectionSpinner;
            this.realTimeUpdateStatusTextView = realTimeUpdateStatusTextView;
            this.realTimeUpdateConnectionStatusTextView = realTimeUpdateConnectionStatusTextView;
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

                JSONObject messages = new JSONObject(sb.toString());
                JSONArray allTrains = messages.getJSONArray(MESSAGES);
                for (int i = 0; i < allTrains.length(); ++i) {
                    JSONArray tripStopArray = allTrains.getJSONArray(i);
                    TripStop tripStop = new TripStop();
                    for (int j = 0; j < tripStopArray.length(); ++j) {
                        JSONObject tripStopRow = tripStopArray.getJSONObject(j);
                        String key = (String) tripStopRow.get(KEY);
                        String value = (String) tripStopRow.get(VALUE);

                        tripStop.consume(key, value);
                    }
                    allTripStops.add(tripStop);
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
