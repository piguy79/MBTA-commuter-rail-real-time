package com.rails.mbta.commuterrail;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.rails.mbta.commuterrail.model.Line;

public class MBTACommuterRailActivity extends Activity {
    public static final String SELECTED_LINE = "SELECTED_LINE";
    public static final String SELECTED_DIRECTION = "SELECTED_DIRECTION";
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

        final Spinner chosenLine = (Spinner) findViewById(R.id.chosenLine);
        ArrayAdapter<Line> commuterRailLinesAdapter = new ArrayAdapter<Line>(this, R.layout.train_selection_item,
                R.id.chosenLineText, Line.values());
        chosenLine.setAdapter(commuterRailLinesAdapter);

        DirectionButtonOnClickListener listener = new DirectionButtonOnClickListener(chosenLine);
        Button inboundButton = (Button) findViewById(R.id.inboundButton);
        Button outboundButton = (Button) findViewById(R.id.outboundButton);
        inboundButton.setOnClickListener(listener);
        outboundButton.setOnClickListener(listener);
    }

    private static class DirectionButtonOnClickListener implements View.OnClickListener {
        private Spinner chosenLine;

        public DirectionButtonOnClickListener(Spinner chosenLine) {
            this.chosenLine = chosenLine;
        }

        public void onClick(View v) {
            int direction = OUTBOUND;
            if (v.getId() == R.id.inboundButton) {
                direction = INBOUND;
            }
            Context context = v.getContext();
            Intent intent = new Intent(context, TrainSelectionView.class);

            Bundle extras = new Bundle();
            extras.putInt(SELECTED_LINE, Line.valueOfName(chosenLine.getSelectedItem().toString()).getLineNumber());
            extras.putInt(SELECTED_DIRECTION, direction);
            intent.putExtras(extras);

            context.startActivity(intent);

        }

    }
}