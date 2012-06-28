package com.rails.mbta.commuterrail.model;

public enum Flag {
    SCH("Determining"), PRE("Known"), APP("Approaching"), ARR("Arrived"), DEP("Departed"), DEL("Delayed");

    private String text;

    private Flag(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
