package com.rails.mbta.commuterrail.views;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.rails.mbta.commuterrail.Common;
import com.rails.mbta.commuterrail.R;
import com.rails.mbta.commuterrail.model.TripStop;
import com.rails.mbta.commuterrail.schedule.Station;

public class RealTimeLineView extends View {

    private Paint linePaint = new Paint();
    private Paint stopPaint = new Paint();
    private Paint stopTextPaint = new Paint();
    private Paint borderPaint = new Paint();

    private static float LINE_X_OFFSET_RATIO = 0.10f;
    private static float LINE_X_END_OFFSET_RATIO = 0.19f;
    private static float LINE_Y_OFFSET_RATIO = 0.08f;
    private static float STOP_RADIUS = 4.0f;

    private List<TrainWithNearestStations> trainWithNearestStations = new ArrayList<TrainWithNearestStations>();
    private List<Station> stations;
    private List<Station> stationsTrunk = new ArrayList<Station>();
    private List<Station> stationsPrimary = new ArrayList<Station>();
    private List<Station> stationsSecondary = new ArrayList<Station>();

    public RealTimeLineView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        init();
    }

    public RealTimeLineView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    public void render(Map<String, TripStop> trainsInMotion) {
        trainWithNearestStations.clear();

        for (Map.Entry<String, TripStop> trainInMotion : trainsInMotion.entrySet()) {
            TreeMap<Double, Station> nearestStation = new TreeMap<Double, Station>();

            for (Station station : stations) {
                double distance = Math.pow(station.stopLat - trainInMotion.getValue().getLatitude(), 2.0);
                distance += Math.pow(station.stopLon - trainInMotion.getValue().getLongitude(), 2.0);
                double dist = Math.sqrt(distance);

                nearestStation.put(dist, station);
            }

            Map.Entry<Double, Station> firstStop = nearestStation.firstEntry();
            trainWithNearestStations.add(new TrainWithNearestStations(trainInMotion.getValue(), nearestStation
                    .remove(nearestStation.firstKey()), nearestStation.remove(nearestStation.firstKey()), firstStop
                    .getKey()));
        }

        invalidate();
    }

    private void init() {
        linePaint.setColor(getResources().getColor(R.color.mbtaPurple));
        stopPaint.setColor(getResources().getColor(R.color.stop_color));
        stopTextPaint.setColor(getResources().getColor(R.color.stop_text_color));
        stopTextPaint.setTextSize(12.0f);
        borderPaint.setColor(getResources().getColor(R.color.border_color));

        stationsTrunk.clear();
        stationsPrimary.clear();
        stationsSecondary.clear();

        stations = Common.trips[0].route.stations;
        for (Station station : stations) {
            if (station.branch.equals("Trunk")) {
                stationsTrunk.add(station);
            } else if (station.branch.equals("Primary")) {
                stationsPrimary.add(station);
            } else if (station.branch.equals("Secondary")) {
                stationsSecondary.add(station);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = canvas.getWidth();
        float height = canvas.getHeight() - 2 * canvas.getHeight() * LINE_Y_OFFSET_RATIO;
        float top = canvas.getHeight() * LINE_Y_OFFSET_RATIO;
        float bottom = top + height;

        /*
         * Render the stations in chunks: Trunk, Primary, Secondary. Latter two
         * are optional.
         */
        if (stationsPrimary.isEmpty() && stationsSecondary.isEmpty()) {
            drawSection(canvas, 0, top, width / 2.0f, bottom, stationsTrunk, true, true, 0.0f);
        } else {
            int maxStops = Math.max(stationsPrimary.size(), stationsSecondary.size()) + stationsTrunk.size();

            float ratioThatIsNotOnTrunk = 1.0f - (float) stationsTrunk.size() / (float) maxStops;
            float halfWidth = width / 2.0f;

            /*
             * Find with section has the 1st stop sequence
             */
            int currentStopSequence = 0;

            float stopDiff = height / ((float) maxStops - 1.0f);

            if (stationsTrunk.get(0).stopSequence == 1) {
                for (Station nextStation : stationsTrunk) {
                    if (nextStation.stopSequence != currentStopSequence + 1) {
                        break;
                    }
                    currentStopSequence++;
                }
            }

            float startBranchY = top;
            if (currentStopSequence != 0) {
                startBranchY += height * ((currentStopSequence + 1.0f) / (float) maxStops);
            }
            float endBranchY = startBranchY + (ratioThatIsNotOnTrunk * (height - stopDiff));

            drawSection(canvas, 0, startBranchY, halfWidth, endBranchY, stationsPrimary,
                    currentStopSequence == 0 ? true : false, false, stopDiff);
            drawSection(canvas, halfWidth, startBranchY, width, endBranchY, stationsSecondary,
                    currentStopSequence == 0 ? true : false, false, stopDiff);

            // Draw trunk last to avoid overlapping connectors
            if (stationsTrunk.get(0).stopSequence == 1) {
                drawSection(canvas, 0, top, halfWidth, startBranchY - stopDiff,
                        stationsTrunk.subList(0, currentStopSequence), true, false, stopDiff);
            }

            if (currentStopSequence != stationsTrunk.size()) {
                drawSection(canvas, 0, endBranchY + stopDiff, halfWidth, bottom,
                        stationsTrunk.subList(currentStopSequence, stationsTrunk.size()), false, true, 0.0f);
            }

            drawTrains(canvas);
        }
    }

    private void drawSection(Canvas canvas, float left, float top, float right, float bottom, List<Station> stations,
            boolean isBeginSection, boolean isEndSection, float nextStopYDiff) {
        float width = right - left;
        float height = bottom - top;

        float lineXOffset = left + width * LINE_X_OFFSET_RATIO;
        float lineXEndOffset = left + width * LINE_X_END_OFFSET_RATIO;

        if (left == 0) {
            canvas.drawRect(lineXOffset, top - STOP_RADIUS, lineXEndOffset, bottom + nextStopYDiff - STOP_RADIUS,
                    linePaint);
        } else {
            canvas.drawRect(lineXOffset, top - STOP_RADIUS, lineXEndOffset, bottom + 2 * STOP_RADIUS, linePaint);

            /*
             * Connect this secondary line back to the trunk
             */

            // Top to trunk
            Path connector = new Path();
            if (!isBeginSection) {
                connector.moveTo(lineXOffset, top + 4);
                connector.lineTo(width * LINE_X_OFFSET_RATIO, top - nextStopYDiff + 4);
                connector.lineTo(width * LINE_X_END_OFFSET_RATIO, top - nextStopYDiff - 5);
                connector.lineTo(lineXEndOffset, top - 5);
                connector.lineTo(lineXOffset, top - +4);
                canvas.drawPath(connector, linePaint);
            }

            // Bottom to trunk
            connector = new Path();
            connector.moveTo(lineXOffset, top + height - 4);
            connector.lineTo(width * LINE_X_OFFSET_RATIO, top + height + nextStopYDiff - 4);
            connector.lineTo(width * LINE_X_END_OFFSET_RATIO, top + height + nextStopYDiff + 5);
            connector.lineTo(lineXEndOffset, top + height + 5);
            connector.lineTo(lineXOffset, top + height - 4);
            canvas.drawPath(connector, linePaint);
        }

        float stopSpacing = 0.0f;
        if (stations.size() > 1) {
            stopSpacing = height / (stations.size() - 1);
        } else {
            stopSpacing = height;
        }

        int i = 0;
        float stopXOffset = lineXOffset + (lineXEndOffset - lineXOffset) / 2;
        float stopYOffset = top;
        float stopTextXOffset = lineXEndOffset + 5;
        for (Station station : stations) {
            float yOffset = stopYOffset + i * stopSpacing;
            float radiusModifier = 0.0f;
            if ((i == 0 && isBeginSection) || (i == stations.size() - 1 && isEndSection)) {
                radiusModifier = 6.0f;
                canvas.drawCircle(stopXOffset, yOffset, STOP_RADIUS + 5.0f + radiusModifier, linePaint);
            }
            canvas.drawCircle(stopXOffset, yOffset, STOP_RADIUS + 1.0f + radiusModifier, borderPaint);
            canvas.drawCircle(stopXOffset, yOffset, STOP_RADIUS + radiusModifier, stopPaint);
            canvas.drawText(station.stopId, stopTextXOffset, yOffset + 2, stopTextPaint);

            for (TrainWithNearestStations nearestTrains : trainWithNearestStations) {
                if (nearestTrains.closestStop.stopId.equals(station.stopId)) {
                    nearestTrains.closestStopYOffset = yOffset;
                    nearestTrains.closestStopXOffset = lineXOffset;
                } else if (nearestTrains.secondClosestStop.stopId.equals(station.stopId)) {
                    nearestTrains.secondClosestStopYOffset = yOffset;
                    nearestTrains.secondClosestStopXOffset = lineXOffset;
                }
            }

            ++i;
        }

    }

    private void drawTrains(Canvas canvas) {
        for (TrainWithNearestStations train : trainWithNearestStations) {
            double latitudeDiff = train.closestStop.stopLat - train.secondClosestStop.stopLat;
            double longitudeDiff = train.closestStop.stopLon - train.secondClosestStop.stopLon;
            double gpsDistanceBetweenStops = Math.sqrt(Math.pow(latitudeDiff, 2.0) + Math.pow(longitudeDiff, 2.0));

            double gpsTrainRatio = train.gpsDistanceToNearestStation / gpsDistanceBetweenStops;

            double canvasYDiff = train.closestStopYOffset - train.secondClosestStopYOffset;
            double canvasXDiff = train.closestStopXOffset - train.secondClosestStopXOffset;

            double trainY = 0.0f;
            double trainX = 0.0f;
            if (canvasYDiff >= 0) {
                trainY = train.closestStopYOffset - canvasYDiff * gpsTrainRatio;
            } else {
                trainY = train.secondClosestStopYOffset + -1.0 * canvasYDiff * gpsTrainRatio;
            }

            if (canvasXDiff >= 0) {
                trainX = train.closestStopXOffset - canvasXDiff * gpsTrainRatio;
            } else {
                trainX = train.secondClosestStopXOffset + -1.0 * canvasXDiff * gpsTrainRatio;
            }

            canvas.drawRect(new RectF((float) trainX, (float) trainY, (float) trainX + 15.0f, (float) trainY + 10.0f),
                    stopTextPaint);
        }
    }

    private static class TrainWithNearestStations {
        public TripStop trainInMotionTripStop;
        public double gpsDistanceToNearestStation = 0.0;
        public Station closestStop;
        public Station secondClosestStop;
        public float closestStopYOffset = -1.0f;
        public float closestStopXOffset = -1.0f;
        public float secondClosestStopYOffset = -1.0f;
        public float secondClosestStopXOffset = -1.0f;

        public TrainWithNearestStations(TripStop trainInMotionTripStop, Station closestStop, Station secondClosestStop,
                double gpsDistanceToNearestStation) {
            super();
            this.trainInMotionTripStop = trainInMotionTripStop;
            this.closestStop = closestStop;
            this.secondClosestStop = secondClosestStop;
            this.gpsDistanceToNearestStation = gpsDistanceToNearestStation;
        }
    }
}
