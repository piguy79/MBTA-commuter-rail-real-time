package com.rails.mbta.commuterrail.schedule;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Route implements Serializable {
    private static final long serialVersionUID = -2245936549180955971L;
    public String routeId;
    public String routeLongName;

    public List<Trip> trips = new ArrayList<Trip>();
}
