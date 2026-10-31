package org.amped.service;

import org.amped.model.recovery.Plan;
import org.amped.model.recovery.Scenario;

/**
 * Part 4 (optional final boss): Operation Recovery.
 * A storm hit. Some trucks are down and some cargo has a deadline.
 * Your job is to plan the next 1 to 3 days. This is optional and much
 * harder than Parts 1 to 3. Start it only when you want a challenge.
 *
 * The full story, the examples and the four OOP goals are in
 * OPERATION-RECOVERY.md.
 *
 * This file starts as a stub: it compiles and returns null, so
 * StarterTests shows FAIL for Part 4 until you write the real code.
 * You may add your own classes next to this one in org.amped.service.
 */
public class RecoveryPlanner {
    public RecoveryPlanner() {
    }

    /**
     * Returns the best plan for this scenario, or null if no valid plan exists.
     */
    public Plan plan(Scenario scenario) {
        // TODO (optional final boss): build the best plan. The rules in short
        // (see OPERATION-RECOVERY.md for the full story):
        //
        // 1. Days are numbered 1 up to scenario.getHorizonDays(). Cargo
        //    arrives in the morning. Trucks leave in the evening.
        // 2. Each cargo ships at most once, in one piece, never before its
        //    inbound day. A cargo you never ship just stays in the warehouse.
        // 3. A trip is one truck on one day. The truck must be available that
        //    day (truck.isAvailableOn(day)).
        // 4. On every trip: the total cargo weight is not above the truck's
        //    capacity, hazmat cargo needs a hazmat-certified truck, and two
        //    cargo whose hazmat classes are a conflict pair never share a
        //    trip. (A class never conflicts with itself.)
        // 5. Every morning, after the new cargo arrives and before any truck
        //    leaves: warehouse.getCurrentWeight() + the weight of all cargo
        //    that has arrived and not shipped yet must not be above the
        //    warehouse capacity. Exactly equal is fine.
        // 6. Score = the sum of the priority of cargo that ships on time (on
        //    its deadline day or earlier). Shipping late is allowed, but it
        //    scores 0 points. It can still be worth it to free up space.
        // 7. The best plan has the highest score. If two plans tie, the one
        //    with fewer trips wins.
        // 8. If no valid plan exists, return null. An empty plan is valid when
        //    the warehouse capacity holds every morning.
        // 9. Do NOT change the warehouse, the scenario's lists or any object
        //    inside them. Read them, copy what you need, and build a new Plan.
        // 10. The same RecoveryPlanner object is used for many scenarios, one
        //    after another. Do not keep leftovers from one call to the next.
        //
        // Limits: up to 12 cargo, 4 trucks, 3 days, capacity up to 1000.
        // Each call must finish in 5 seconds.
        return null;
    }
}
