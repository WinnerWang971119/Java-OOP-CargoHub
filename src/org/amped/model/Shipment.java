package org.amped.model;

/**
 * One line of a batch: "move this many units of this cargo".
 * A shipment is either inbound (comes into the warehouse) or outbound
 * (leaves the warehouse). It can also carry a priority, which only
 * matters in Part 2.
 */
public class Shipment {
    private Cargo cargo;
    private int quantity;
    private boolean inbound;
    private int priority;

    /**
     * @param cargo    what is being moved
     * @param quantity how many units (always 1 or more)
     * @param inbound  true = comes in, false = goes out
     * @param priority how much we want this shipment (bigger = better)
     */
    public Shipment(Cargo cargo, int quantity, boolean inbound, int priority) {
        this.cargo = cargo;
        this.quantity = quantity;
        this.inbound = inbound;
        this.priority = priority;
    }

    /** Same as the constructor above, with priority 0. */
    public Shipment(Cargo cargo, int quantity, boolean inbound) {
        this(cargo, quantity, inbound, 0);
    }

    /** Returns the cargo being moved. */
    public Cargo getCargo() {
        return cargo;
    }

    /** Returns the SKU of the cargo (shortcut for getCargo().getSku()). */
    public String getSku() {
        return cargo.getSku();
    }

    /** Returns how many units are moved. */
    public int getQuantity() {
        return quantity;
    }

    /** Returns true if the shipment comes in, false if it goes out. */
    public boolean isInbound() {
        return inbound;
    }

    /** Returns the priority (bigger = better). It is 0 if none was given. */
    public int getPriority() {
        return priority;
    }

    /** Returns the total weight of this shipment: quantity * unit weight. */
    public int getWeight() {
        return quantity * cargo.getUnitWeight();
    }
}
