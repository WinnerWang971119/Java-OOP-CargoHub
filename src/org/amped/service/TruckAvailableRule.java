package org.amped.service;

import java.util.List;
import org.amped.model.recovery.RecoveryCargo;
import org.amped.model.recovery.Scenario;
import org.amped.model.recovery.Truck;

/** A truck only leaves on a day it is available. */
public class TruckAvailableRule implements TripRule {
    @Override
    public boolean allows(Scenario scenario, Truck truck, int day, List<RecoveryCargo> load) {
        return truck.isAvailableOn(day);
    }
}
