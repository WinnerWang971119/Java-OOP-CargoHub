package org.amped.model.recovery;

import java.util.ArrayList;
import org.amped.model.Warehouse;

/**
 * Part 4 (optional final boss): everything you need to plan the days after
 * the storm. Read it, but do not change it or anything inside it.
 *
 * The Scenario keeps its own copy of each list it is given. The getters give
 * you a fresh copy of the list, but the objects inside (cargo, trucks,
 * warehouse) are the real ones.
 */
public class Scenario {
    private Warehouse warehouse;
    private int horizonDays;
    private ArrayList<RecoveryCargo> cargo;
    private ArrayList<Truck> trucks;
    private ArrayList<String[]> hazmatConflicts;

    /**
     * @param warehouse       the warehouse; what is already in it takes up room all the time
     * @param horizonDays     how many days to plan (1 to 3); days are numbered 1, 2, 3
     * @param cargo           all the cargo that will arrive (null means none)
     * @param trucks          all the trucks (null means none)
     * @param hazmatConflicts pairs of hazmat classes that must never share a trip
     *                        (null means none)
     */
    public Scenario(Warehouse warehouse, int horizonDays, ArrayList<RecoveryCargo> cargo,
            ArrayList<Truck> trucks, ArrayList<String[]> hazmatConflicts) {
        this.warehouse = warehouse;
        this.horizonDays = horizonDays;
        if (cargo == null) {
            this.cargo = new ArrayList<>();
        } else {
            this.cargo = new ArrayList<>(cargo);
        }
        if (trucks == null) {
            this.trucks = new ArrayList<>();
        } else {
            this.trucks = new ArrayList<>(trucks);
        }
        if (hazmatConflicts == null) {
            this.hazmatConflicts = new ArrayList<>();
        } else {
            this.hazmatConflicts = new ArrayList<>(hazmatConflicts);
        }
    }

    /** Returns the warehouse. Do NOT change it. */
    public Warehouse getWarehouse() {
        return warehouse;
    }

    /** Returns how many days to plan (days are numbered 1 to this number). */
    public int getHorizonDays() {
        return horizonDays;
    }

    /** Returns a copy of the list of cargo (the cargo objects are the same). */
    public ArrayList<RecoveryCargo> getCargo() {
        return new ArrayList<>(cargo);
    }

    /** Returns a copy of the list of trucks (the truck objects are the same). */
    public ArrayList<Truck> getTrucks() {
        return new ArrayList<>(trucks);
    }

    /**
     * Returns a copy of the list of hazmat conflicts. Each String[] holds
     * exactly 2 different hazmat classes, and their order does not matter.
     * Cargo in two conflicting classes never share a trip.
     */
    public ArrayList<String[]> getHazmatConflicts() {
        return new ArrayList<>(hazmatConflicts);
    }
}
