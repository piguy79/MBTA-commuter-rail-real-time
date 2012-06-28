package com.rails.mbta.commuterrail.schedule;

import java.io.Serializable;

import org.joda.time.LocalDate;

public class Calendar implements Serializable {
    private static final long serialVersionUID = 2162784922463554330L;
    public String serviceId;
    // value at row 0 is unused.  we want index 1 - 7 to match a day of the week
    public boolean[] serviceDays = new boolean[8];
    public LocalDate startDate;
    public LocalDate endDate;

}
