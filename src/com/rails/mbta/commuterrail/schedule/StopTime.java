package com.rails.mbta.commuterrail.schedule;

import java.io.Serializable;

import org.joda.time.LocalTime;

import com.rails.mbta.commuterrail.Common;

public class StopTime implements Serializable {
    private static final long serialVersionUID = -3444215332295116873L;
    public Trip trip;
    public LocalTime arrivalTime;
    public LocalTime departureTime;
    public String stopId;
    public Stop stop;
    public int stopSequence;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Common.TIME_FORMATTER.print(arrivalTime));
        sb.append(" - ");
        sb.append(stopId);

        return sb.toString();
    }
}
