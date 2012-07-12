package com.rails.mbta.commuterrail;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ListIterator;

import org.joda.time.LocalDate;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
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

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        int mbtaPurpleId = getResources().getColor(R.color.mbtaPurple);

        TextView mb = (TextView) findViewById(R.id.mbText);
        mb.setBackgroundColor(mbtaPurpleId);
        mb.setGravity(Gravity.RIGHT);

        TextView ta = (TextView) findViewById(R.id.taText);
        ta.setTextColor(mbtaPurpleId);
        ta.setGravity(Gravity.LEFT);

        final Spinner chosenLineSpinner = (Spinner) findViewById(R.id.chosenLine);
        ArrayAdapter<Line> commuterRailLinesAdapter = new ArrayAdapter<Line>(this, R.layout.train_selection_spinner,
                R.id.chosenLineText, Line.values());
        commuterRailLinesAdapter.setDropDownViewResource(R.layout.train_selection_item);
        chosenLineSpinner.setAdapter(commuterRailLinesAdapter);

        Button goButton = (Button) findViewById(R.id.goButton);
        goButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                LoadScheduleInformation scheduleLoader = new LoadScheduleInformation(MBTACommuterRailActivity.this,
                        ((Line) chosenLineSpinner.getSelectedItem()).getLineNumber());
                scheduleLoader.execute("");
            }
        });
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

            Intent intent = new Intent(activity, TrainSelectionView.class);

            Bundle extras = new Bundle();
            extras.putInt(SELECTED_LINE, selectedLine);
            intent.putExtras(extras);

            progressDialog.dismiss();

            activity.startActivity(intent);
        }
    }
}