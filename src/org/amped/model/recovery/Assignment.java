package org.amped.model.recovery;

/**
 * Part 4 (optional final boss): one decision in a plan.
 * "Put this cargo on this truck on this day (it leaves that evening)."
 */
public class Assignment {
    private RecoveryCargo cargo;
    private Truck truck;
    private int day;

    /**
     * @param cargo the cargo to ship
     * @param truck the truck that carries it
     * @param day   the day the truck leaves (in the evening)
     */
    public Assignment(RecoveryCargo cargo, Truck truck, int day) {
        this.cargo = cargo;
        this.truck = truck;
        this.day = day;
    }

    /** Returns the cargo that ships. */
    public RecoveryCargo getCargo() {
        return cargo;
    }

    /** Returns the truck that carries the cargo. */
    public Truck getTruck() {
        return truck;
    }

    /** Returns the day the truck leaves. */
    public int getDay() {
        return day;
    }
}
