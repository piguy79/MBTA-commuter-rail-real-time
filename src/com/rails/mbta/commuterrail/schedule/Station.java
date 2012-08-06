package com.rails.mbta.commuterrail.schedule;

import java.io.Serializable;

public class Station implements Serializable {
    private static final long serialVersionUID = -4021771809193027747L;
    public String routeLongName;
    public int directionId;
    public int stopSequence;
    public String stopId;
    public double stopLat;
    public double stopLon;
    public String branch;
}
