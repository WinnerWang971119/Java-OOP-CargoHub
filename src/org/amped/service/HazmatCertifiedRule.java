package org.amped.service;

import java.util.List;
import org.amped.model.recovery.RecoveryCargo;
import org.amped.model.recovery.Scenario;
import org.amped.model.recovery.Truck;

/** Hazmat cargo only rides on a hazmat-certified truck. */
public class HazmatCertifiedRule implements TripRule {
    @Override
    public boolean allows(Scenario scenario, Truck truck, int day, List<RecoveryCargo> load) {
        if (truck.isHazmatCertified()) {
            return true;
        }
        for (RecoveryCargo cargo : load) {
            if (cargo.isHazmat()) {
                return false;
            }
        }
        return true;
    }
}
