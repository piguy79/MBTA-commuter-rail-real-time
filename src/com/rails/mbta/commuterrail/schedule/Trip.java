package com.rails.mbta.commuterrail.schedule;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.rails.mbta.commuterrail.Common;

public class Trip implements Serializable {
    private static final long serialVersionUID = -8028653882366417781L;
    public Route route;
    public List<StopTime> stopTimes = new ArrayList<StopTime>();
    public String tripId;
    public String tripHeadsign;
    public int directionId;
    public String serviceId;
    public Calendar service;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!stopTimes.isEmpty()) {
            sb.append(Common.TIME_FORMATTER.print(stopTimes.get(0).departureTime)).append(" to ");
        }
        return sb.append(tripHeadsign).toString();
    }

}
