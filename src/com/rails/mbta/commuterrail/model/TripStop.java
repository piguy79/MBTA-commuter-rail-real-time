package com.rails.mbta.commuterrail.model;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Date;

public class TripStop {
    private Date timestamp;
    private String trip;
    private String destination;
    private String stop;
    private Date scheduled;
    private Flag flag;
    private Integer vehicle;
    private Double longitude;
    private Double latitude;
    private Integer direction;
    private Integer speed;
    private Integer lateness;

    public void consume(String key, String value) {
        Method[] methods = getClass().getMethods();

        for (Method method : methods) {
            if (method.getName().equals("set" + key)) {
                Object[] args = new Object[1];
                Class parameter = method.getParameterTypes()[0];

                if (value.trim().isEmpty()) {
                    args[0] = null;
                } else if (parameter == Integer.class) {
                    args[0] = new Integer(value);
                } else if (parameter == Double.class) {
                    args[0] = new Double(value);
                } else if (parameter == Date.class) {
                    args[0] = new Date(Long.parseLong(value));
                } else if (parameter == Destination.class) {
                    args[0] = Destination.valueOf(value);
                } else if (parameter == Flag.class) {
                    args[0] = Flag.valueOf(value.toUpperCase());
                } else if (parameter == String.class) {
                    args[0] = value;
                } else {

                    throw new IllegalArgumentException("Unable to set key: " + key + " with value: " + value);
                }

                try {
                    method.invoke(this, args);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getTrip() {
        return trip;
    }

    public void setTrip(String trip) {
        this.trip = trip;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getStop() {
        return stop;
    }

    public void setStop(String stop) {
        this.stop = stop;
    }

    public Date getScheduled() {
        return scheduled;
    }

    public void setScheduled(Date scheduled) {
        this.scheduled = scheduled;
    }

    public Flag getFlag() {
        return flag;
    }

    public void setFlag(Flag flag) {
        this.flag = flag;
    }

    public Integer getVehicle() {
        return vehicle;
    }

    public void setVehicle(Integer vehicle) {
        this.vehicle = vehicle;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Integer getDirection() {
        return direction;
    }

    public void setDirection(Integer direction) {
        this.direction = direction;
    }

    public Integer getSpeed() {
        return speed;
    }

    public void setSpeed(Integer speed) {
        this.speed = speed;
    }

    public Integer getLateness() {
        return lateness;
    }

    public void setLateness(Integer lateness) {
        this.lateness = lateness;
    }
}
