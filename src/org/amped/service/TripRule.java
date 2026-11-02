package org.amped.service;

import java.util.List;
import org.amped.model.recovery.RecoveryCargo;
import org.amped.model.recovery.Scenario;
import org.amped.model.recovery.Truck;

/**
 * One safety or transport restriction for a single trip (one truck on one evening).
 * To add a new restriction, write a new class that implements this interface
 * and pass it to the RecoveryPlanner. The planner itself does not change.
 */
public interface TripRule {
    /** Returns true if this truck may carry exactly this load on this day. */
    boolean allows(Scenario scenario, Truck truck, int day, List<RecoveryCargo> load);
}
