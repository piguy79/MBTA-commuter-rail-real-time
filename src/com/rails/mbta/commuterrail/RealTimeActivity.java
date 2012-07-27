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
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import com.rails.mbta.commuterrail.model.TripStop;
import com.rails.mbta.commuterrail.schedule.Trip;
import com.rails.mbta.commuterrail.views.RealTimeLineView;

public class RealTimeActivity extends Activity {

    private static final String REAL_TIME_WARNING_SHOWN = "realTimeWarningShown";
    private static final String PREF_STORAGE_NAME = "trainSelectionPrefs";
    private static final String LAST_UPDATE = "Last update: ";
    private static final String LAST_UPDATE_RETRIEVING = LAST_UPDATE + "Retrieving...";

    private static final long REAL_TIME_UPDATE_IN_MILLIS = 12000;
    private Handler realTimeUpdateHandler = new Handler();
    private RealTimeUpdateRunnable realTimeUpdateRunnable;

    private int defaultColor;
    private int trainDelayLateColor;
    private int trainDelayWarningColor;

    private int selectedLine;

    private TextView realTimeUpdateConnectionStatusTextView;
    private TextView realTimeUpdateStatusTextView;
    private RealTimeLineView realTimeLineView;

    private Trip[] trips;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null || intent.getExtras() == null || Common.trips == null) {
            Intent lineSelectionPageIntent = new Intent(this, MBTACommuterRailActivity.class);
            startActivity(lineSelectionPageIntent);
            return;
        }

        Bundle extras = intent.getExtras();
        selectedLine = extras.getInt(MBTACommuterRailActivity.SELECTED_LINE);

        setContentView(R.layout.activity_real_time);

        trainDelayLateColor = getResources().getColor(R.color.train_delay_late);
        trainDelayWarningColor = getResources().getColor(R.color.train_delay_warning);

        trips = Common.trips;

        realTimeUpdateStatusTextView = (TextView) findViewById(R.id.realTimeUpdateStatusTextView);
        realTimeUpdateConnectionStatusTextView = (TextView) findViewById(R.id.realTimeUpdateConnectionStatusTextView);
        realTimeLineView = (RealTimeLineView) findViewById(R.id.realTimeLineView1);
        
        defaultColor = realTimeUpdateStatusTextView.getTextColors().getDefaultColor();
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
                            RealTimeActivity.this.finish();
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        }

        startRealTimeUpdatePosts();

        super.onResume();
    }

    private void startRealTimeUpdatePosts() {
        stopRealTimeUpdatePosts();

        realTimeUpdateStatusTextView.setText(LAST_UPDATE_RETRIEVING);
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        realTimeUpdateRunnable = new RealTimeUpdateRunnable(selectedLine, connectivityManager,
                realTimeUpdateStatusTextView, realTimeUpdateConnectionStatusTextView);
        realTimeUpdateHandler.post(realTimeUpdateRunnable);
    }

    private void stopRealTimeUpdatePosts() {
        if (realTimeUpdateRunnable != null) {
            realTimeUpdateRunnable.kill();
        }
        realTimeUpdateHandler.removeCallbacksAndMessages(null);
    }

    private class RealTimeUpdateRunnable implements Runnable {
        private int selectedLine;
        private ConnectivityManager connectivityManager;
        private TextView realTimeUpdateStatusTextView;
        private TextView realTimeUpdateConnectionStatusTextView;
        private boolean shouldKill = false;

        public RealTimeUpdateRunnable(int selectedLine, ConnectivityManager connectivityManager,
                TextView realTimeUpdateStatusTextView, TextView realTimeUpdateConnectionStatusTextView) {
            this.selectedLine = selectedLine;
            this.connectivityManager = connectivityManager;
            this.realTimeUpdateStatusTextView = realTimeUpdateStatusTextView;
            this.realTimeUpdateConnectionStatusTextView = realTimeUpdateConnectionStatusTextView;
        }

        public void kill() {
            this.shouldKill = true;
        }

        public void run() {
            if (!shouldKill) {
                new LoadLineInformation(connectivityManager, realTimeUpdateStatusTextView,
                        realTimeUpdateConnectionStatusTextView, this).execute(Integer.toString(selectedLine));
            }

        }
    }

    private class LoadLineInformation extends AsyncTask<String, Void, List<TripStop>> {

        private static final String KEY = "Key";
        private static final String VALUE = "Value";
        private static final String MESSAGES = "Messages";
        private ConnectivityManager connectivityManager;
        private TextView realTimeUpdateStatusTextView;
        private TextView realTimeUpdateConnectionStatusTextView;
        private RealTimeUpdateRunnable realTimeUpdateRunnable;

        public LoadLineInformation(ConnectivityManager connectivityManager, TextView realTimeUpdateStatusTextView,
                TextView realTimeUpdateConnectionStatusTextView, RealTimeUpdateRunnable realTimeUpdateRunnable) {
            this.connectivityManager = connectivityManager;
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
            if (result == null) {
                realTimeUpdateConnectionStatusTextView.setText("No connection");
            } else {
                /*
                 * Find out where all trains are.
                 */
                Map<String, TripStop> trainsInMotion = new HashMap<String, TripStop>();
                for (ListIterator<TripStop> iter = result.listIterator(); iter.hasNext();) {
                    TripStop tripStop = iter.next();
                    if (tripStop.getTrip() != null && tripStop.getLatitude() != null && tripStop.getLongitude() != null) {
                        trainsInMotion.put(tripStop.getTrip(), tripStop);
                    }
                }

                realTimeUpdateConnectionStatusTextView.setText("");
                realTimeLineView.render(trainsInMotion);
                
//                for (int i = 0; i < adapter.getCount(); ++i) {
//                    HashMap<String, String> row = (HashMap<String, String>) adapter.getItem(i);
//                    row.put(ACTUAL_TIME, "");
//                    row.put(ACTUAL_TIME_COLOR_ID, "");
//
//                    for (TripStop tripStop : result) {
//                        if (tripStop.getStop().equals(row.get(STOP_NAME))) {
//                            String realTimeInfo = "";
//                            Flag statusFlag = tripStop.getFlag();
//                            if (statusFlag == Flag.PRE || statusFlag == Flag.DEL) {
//                                Integer late = tripStop.getLateness();
//                                if (late == null) {
//                                    realTimeInfo = statusFlag.toString();
//                                } else {
//                                    LocalTime scheduledTime = Common.TIME_FORMATTER.parseLocalTime(row
//                                            .get(SCHEDULED_TIME));
//                                    scheduledTime = scheduledTime.plusSeconds(late);
//                                    realTimeInfo = ETA + Common.TIME_FORMATTER.print(scheduledTime);
//
//                                    if (late >= 180 && late < 300) {
//                                        row.put(ACTUAL_TIME_COLOR_ID, "" + trainDelayWarningColor);
//                                    } else if (late >= 300) {
//                                        row.put(ACTUAL_TIME_COLOR_ID, "" + trainDelayLateColor);
//                                    }
//
//                                }
//                            } else {
//                                realTimeInfo = statusFlag.toString();
//                            }
//
//                            row.put(ACTUAL_TIME, realTimeInfo);
//                        }
//                    }
//                }

                realTimeUpdateStatusTextView.setText(LAST_UPDATE
                        + Common.TIME_FORMATTER_W_SECONDS.print(new LocalTime()));
            }

            realTimeUpdateHandler.postDelayed(realTimeUpdateRunnable, REAL_TIME_UPDATE_IN_MILLIS);
        }
    }

}
