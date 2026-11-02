package org.amped.service;

import java.util.List;
import org.amped.model.recovery.RecoveryCargo;
import org.amped.model.recovery.Scenario;
import org.amped.model.recovery.Truck;

/** The load on one trip is not above the truck's capacity. */
public class TruckCapacityRule implements TripRule {
    @Override
    public boolean allows(Scenario scenario, Truck truck, int day, List<RecoveryCargo> load) {
        long total = 0;
        for (RecoveryCargo cargo : load) {
            total += cargo.getWeight();
        }
        return total <= truck.getCapacity();
    }
}
