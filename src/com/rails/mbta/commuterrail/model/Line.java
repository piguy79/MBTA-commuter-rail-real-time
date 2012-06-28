package com.rails.mbta.commuterrail.model;

public enum Line {
    GREENBUSH(1, "Greenbush"), KINGSTON_PLYMOUTH(2, "Kingston/Plymouth"), MIDDLEBOROUGH_LAKEVILLE(3,
            "Middleborough/Lakeville"), FAIRMOUNT(4, "Fairmount"), PROVIDENCE_STOUGHTON(5, "Providence/Stoughton"), FRANKLIN(
            6, "Franklin"), NEEDHAM(7, "Needham"), FRAMINGHAM_WORCESTER(8, "Framingham/Worcester"), FITCHBURG(9,
            "Fitchburg/South Acton"), LOWELL(10, "Lowell"), HAVERHILL(11, "Haverhill"), NEWBURYPORT_ROCKPORT(12,
            "Newburyport/Rockport");

    private int lineNumber;
    private String friendlyName;

    private Line(int lineNumber, String friendlyName) {
        this.lineNumber = lineNumber;
        this.friendlyName = friendlyName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public String toString() {
        return friendlyName;
    }

    public static Line valueOfName(String friendlyName) {
        for (Line line : Line.values()) {
            if (line.toString().equals(friendlyName)) {
                return line;
            }
        }

        return null;
    }

    public static Line valueOfNumber(int lineNumber) {
        for (Line line : Line.values()) {
            if (line.getLineNumber() == lineNumber) {
                return line;
            }
        }

        return null;
    }
}
