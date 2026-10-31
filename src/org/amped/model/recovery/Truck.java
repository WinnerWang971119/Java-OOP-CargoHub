package org.amped.model.recovery;

import java.util.ArrayList;

/**
 * Part 4 (optional final boss): one truck.
 * A truck can leave once on each day it is available (in the evening).
 * One truck on one day is called a "trip".
 */
public class Truck {
    private String id;
    private int capacity;
    private boolean hazmatCertified;
    private ArrayList<Integer> availableDays;

    /**
     * @param id              the name of this truck, like "T1"
     * @param capacity        the most weight one trip can carry
     * @param hazmatCertified true if this truck may carry hazmat cargo
     * @param availableDays   the days this truck can leave, like [1, 3]
     *                        (the truck keeps its own copy; null means no days)
     */
    public Truck(String id, int capacity, boolean hazmatCertified, ArrayList<Integer> availableDays) {
        this.id = id;
        this.capacity = capacity;
        this.hazmatCertified = hazmatCertified;
        if (availableDays == null) {
            this.availableDays = new ArrayList<>();
        } else {
            this.availableDays = new ArrayList<>(availableDays);
        }
    }

    /** Returns the name of this truck. */
    public String getId() {
        return id;
    }

    /** Returns the most weight one trip of this truck can carry. */
    public int getCapacity() {
        return capacity;
    }

    /** Returns true if this truck may carry hazmat cargo. */
    public boolean isHazmatCertified() {
        return hazmatCertified;
    }

    /** Returns true if this truck can leave on this day. */
    public boolean isAvailableOn(int day) {
        for (int availableDay : availableDays) {
            if (availableDay == day) {
                return true;
            }
        }
        return false;
    }

    /** Returns a copy of the list of days this truck can leave. */
    public ArrayList<Integer> getAvailableDays() {
        return new ArrayList<>(availableDays);
    }
}
