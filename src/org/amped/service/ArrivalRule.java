package org.amped.service;

import java.util.List;
import org.amped.model.recovery.RecoveryCargo;
import org.amped.model.recovery.Scenario;
import org.amped.model.recovery.Truck;

/** Cargo never ships before its inbound day or after the last planned day. */
public class ArrivalRule implements TripRule {
    @Override
    public boolean allows(Scenario scenario, Truck truck, int day, List<RecoveryCargo> load) {
        if (day < 1 || day > scenario.getHorizonDays()) {
            return false;
        }
        for (RecoveryCargo cargo : load) {
            if (cargo.getInboundDay() > day) {
                return false;
            }
        }
        return true;
    }
}
