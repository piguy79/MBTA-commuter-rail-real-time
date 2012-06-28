package com.rails.mbta.commuterrail.schedule;

import java.io.Serializable;

public class Stop implements Serializable {
    private static final long serialVersionUID = 1144626477767489530L;
    public String stopId;
    public String stopName;
    public String stopLon;
    public String stopLat;

}
