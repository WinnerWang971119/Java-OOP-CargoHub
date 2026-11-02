package org.amped.service;

import java.util.List;
import org.amped.model.recovery.RecoveryCargo;
import org.amped.model.recovery.Scenario;
import org.amped.model.recovery.Truck;

/** Two cargo whose hazmat classes form a conflict pair never share a trip. */
public class HazmatConflictRule implements TripRule {
    @Override
    public boolean allows(Scenario scenario, Truck truck, int day, List<RecoveryCargo> load) {
        List<String[]> conflicts = scenario.getHazmatConflicts();
        for (int i = 0; i < load.size(); i++) {
            for (int j = i + 1; j < load.size(); j++) {
                if (inConflict(conflicts, load.get(i).getHazmatClass(), load.get(j).getHazmatClass())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean inConflict(List<String[]> conflicts, String a, String b) {
        if (a.equals(b)) {
            return false; // A class never conflicts with itself.
        }
        for (String[] pair : conflicts) {
            if (pair == null || pair.length < 2) {
                continue;
            }
            if ((a.equals(pair[0]) && b.equals(pair[1])) || (a.equals(pair[1]) && b.equals(pair[0]))) {
                return true;
            }
        }
        return false;
    }
}
