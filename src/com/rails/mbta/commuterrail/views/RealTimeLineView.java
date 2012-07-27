package com.rails.mbta.commuterrail.views;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.rails.mbta.commuterrail.Common;
import com.rails.mbta.commuterrail.R;
import com.rails.mbta.commuterrail.model.TripStop;
import com.rails.mbta.commuterrail.schedule.Stop;
import com.rails.mbta.commuterrail.schedule.StopTime;

public class RealTimeLineView extends View {

    private Paint linePaint = new Paint();
    private Paint stopPaint = new Paint();
    private Paint stopTextPaint = new Paint();
    private Paint borderPaint = new Paint();

    private static float LINE_X_OFFSET_RATIO = 0.10f;
    private static float LINE_X_END_OFFSET_RATIO = 0.19f;
    private static float LINE_Y_OFFSET_RATIO = 0.08f;
    private static float STOP_RADIUS = 4.0f;

    private List<TrainWithNearestStops> trainWithNearestStops = new ArrayList<TrainWithNearestStops>();
    private Map<Integer, Stop> stops = new TreeMap<Integer, Stop>();

    public RealTimeLineView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        init();
    }

    public RealTimeLineView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    public void render(Map<String, TripStop> trainsInMotion) {
        trainWithNearestStops.clear();

        for (Map.Entry<String, TripStop> trainInMotion : trainsInMotion.entrySet()) {
            TreeMap<Double, Stop> nearestStops = new TreeMap<Double, Stop>();

            for (Map.Entry<Integer, Stop> stop : stops.entrySet()) {
                BigDecimal stopLat = new BigDecimal(stop.getValue().stopLat);
                BigDecimal stopLon = new BigDecimal(stop.getValue().stopLon);

                BigDecimal distance = stopLat.subtract(trainInMotion.getValue().getLatitude()).pow(2);
                distance = distance.add(stopLon.subtract(trainInMotion.getValue().getLongitude()).pow(2));
                double dist = Math.sqrt(distance.doubleValue());

                nearestStops.put(dist, stop.getValue());
            }

            trainWithNearestStops.add(new TrainWithNearestStops(trainInMotion.getValue(), nearestStops
                    .remove(nearestStops.firstKey()), nearestStops.remove(nearestStops.firstKey())));
        }

        invalidate();
    }

    private void init() {
        linePaint.setColor(getResources().getColor(R.color.mbtaPurple));
        stopPaint.setColor(getResources().getColor(R.color.stop_color));
        stopTextPaint.setColor(getResources().getColor(R.color.stop_text_color));
        stopTextPaint.setTextSize(12.0f);
        borderPaint.setColor(getResources().getColor(R.color.border_color));

        for (int i = 0; i < Common.trips.length; ++i) {
            /*
             * Only read stops in one direction, since the sequence id is
             * reversed for trips in the opposing direction.
             */
            if (Common.trips[i].directionId == 0) {
                continue;
            }
            for (int j = 0; j < Common.trips[i].stopTimes.size(); ++j) {
                StopTime stopTime = Common.trips[i].stopTimes.get(j);
                stops.put(stopTime.stopSequence, stopTime.stop);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = canvas.getWidth();
        float height = canvas.getHeight();

        float lineXOffset = width * LINE_X_OFFSET_RATIO;
        float lineXEndOffset = width * LINE_X_END_OFFSET_RATIO;
        float lineYOffset = height * LINE_Y_OFFSET_RATIO;
        float lineHeight = height - lineYOffset - lineYOffset;

        canvas.drawRect(lineXOffset, lineYOffset, lineXEndOffset, height - lineYOffset, linePaint);

        float stopSpacing = lineHeight / (stops.size() - 1);
        int i = 0;
        float stopXOffset = lineXOffset + (lineXEndOffset - lineXOffset) / 2;
        float stopYOffset = lineYOffset;
        float stopTextXOffset = lineXEndOffset + 5;
        for (Map.Entry<Integer, Stop> entrySet : stops.entrySet()) {
            float yOffset = stopYOffset + i * stopSpacing;
            float radiusModifier = 0.0f;
            if (i == 0 || i == stops.size() - 1) {
                radiusModifier = 7.0f;
                canvas.drawCircle(stopXOffset, yOffset, STOP_RADIUS + 5.0f + radiusModifier, linePaint);
            }
            canvas.drawCircle(stopXOffset, yOffset, STOP_RADIUS + 1.0f + radiusModifier, borderPaint);
            canvas.drawCircle(stopXOffset, yOffset, STOP_RADIUS + radiusModifier, stopPaint);
            canvas.drawText(entrySet.getValue().stopName, stopTextXOffset, yOffset + 2, stopTextPaint);

            for (TrainWithNearestStops nearestTrains : trainWithNearestStops) {
                if (nearestTrains.closestStop.stopName.equals(entrySet.getValue().stopName)) {
                    nearestTrains.firstStopYOffset = yOffset;
                } else if (nearestTrains.secondClosestStop.stopName.equals(entrySet.getValue().stopName)) {
                    nearestTrains.lastStopYOffset = yOffset;
                }
            }

            ++i;
        }

        for (TrainWithNearestStops nearestTrain : trainWithNearestStops) {
            canvas.drawRect(new RectF(lineXOffset - 3, nearestTrain.firstStopYOffset, lineXEndOffset + 3,
                    nearestTrain.firstStopYOffset + 10), stopTextPaint);
        }
    }

    private static class TrainWithNearestStops {
        public TripStop trainInMotionTripStop;
        public Stop closestStop;
        public Stop secondClosestStop;
        public float firstStopYOffset = -1.0f;
        public float lastStopYOffset = -1.0f;

        public TrainWithNearestStops(TripStop trainInMotionTripStop, Stop closestStop, Stop secondClosestStop) {
            super();
            this.trainInMotionTripStop = trainInMotionTripStop;
            this.closestStop = closestStop;
            this.secondClosestStop = secondClosestStop;
        }
    }
}
