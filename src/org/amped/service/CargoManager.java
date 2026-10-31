package org.amped.service;

import java.util.ArrayList;
import java.util.List;
import org.amped.model.Shipment;
import org.amped.model.Warehouse;

/**
 * The brain of CargoHub. This is the ONLY file you need to edit.
 * Each method below starts as a stub: it compiles and returns a dummy value,
 * so StarterTests shows FAIL until you write the real code.
 */
public class CargoManager {
    private Warehouse warehouse;

    public CargoManager(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    /**
     * Part 1 (required): apply a whole batch of shipments, all or nothing.
     * Returns true if the whole batch was applied, false if it was rejected.
     */
    public boolean processBatch(List<Shipment> shipments) {
        // TODO: process the batch, all or nothing.
        //
        // 1. For each SKU, add up the inbound quantities and subtract the
        //    outbound quantities. That is its "net" change.
        //    The order of the shipments inside the batch does not matter.
        // 2. The batch is valid only if BOTH of these are true:
        //    a) For every SKU: current stock + net is not below 0.
        //       (A SKU the warehouse never had has stock 0, so shipping it
        //       out only works if the same batch brings enough of it in.)
        //    b) The weight after the batch is not above the capacity.
        //       Weight after = current weight + the sum of (net * unit weight).
        //       Exactly equal to the capacity is fine.
        // 3. Valid: apply every change to the warehouse and return true.
        //    Not valid: change NOTHING and return false.
        // 4. An empty batch is valid: return true and change nothing.
        //
        // Tip: check everything first. Only then touch the warehouse.
        return false;
    }

    /**
     * Part 2 (required): pick the best inbound shipments that still fit.
     * Returns the chosen shipments.
     */
    public List<Shipment> optimizeInbound(List<Shipment> candidates) {
        // TODO: choose which candidates to accept.
        //
        // 1. Your budget is the warehouse's free capacity.
        // 2. Pick some of the candidates (maybe not all) so that:
        //    - their total getWeight() is not above the budget
        //      (exactly equal is fine), and
        //    - their total getPriority() is as big as possible.
        // 3. Return the chosen Shipment objects themselves (no copies), each
        //    one at most once, in any order.
        // 4. Do NOT change the warehouse. Only return the list.
        // 5. No candidates, or nothing fits: return an empty list.
        //
        // Limits: up to 30 candidates, capacity up to 1000, weights and
        // priorities are whole numbers 1 or more. Each call must finish in
        // 2 seconds, so trying every possible subset is too slow.
        //
        // Warning: grabbing the best-looking shipment first does not always
        // give the best total.
        return new ArrayList<>();
    }

    /**
     * Part 3 (optional bonus): load shipments onto as few trucks as possible.
     * Returns a list of trucks, each truck being a list of shipments.
     */
    public List<List<Shipment>> planTrucks(List<Shipment> shipments, int truckCapacity, List<String[]> conflicts) {
        // TODO (optional bonus, much harder than Parts 1 and 2):
        // put every shipment on a truck, using the fewest trucks possible.
        //
        // 1. Every shipment goes on exactly one truck (the same objects).
        // 2. One truck can carry at most truckCapacity in total getWeight().
        // 3. "conflicts" lists SKU pairs that must never share a truck
        //    (hazmat!). Each String[] holds exactly 2 different SKUs, and
        //    their order does not matter. A SKU never conflicts with itself,
        //    so two shipments of the same SKU may share a truck.
        // 4. Use the minimum number of trucks. No empty trucks.
        // 5. If any single shipment is heavier than truckCapacity, return null.
        // 6. No shipments: return an empty list.
        // 7. Do NOT change the warehouse.
        //
        // Limits: up to 15 shipments, truckCapacity up to 1000, up to 30
        // conflicts. Each call must finish in 3 seconds.
        //
        // Warning: "biggest first" can need one truck more than the best plan.
        return null;
    }
}
