package org.amped.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.amped.model.recovery.Assignment;
import org.amped.model.recovery.Plan;
import org.amped.model.recovery.RecoveryCargo;
import org.amped.model.recovery.Scenario;
import org.amped.model.recovery.Truck;

/**
 * Part 4: Operation Recovery. Finds the optimal plan exactly.
 *
 * Idea: the only thing that matters between days is WHICH cargo has shipped
 * (a bitmask, at most 2^12 = 4096 states). So:
 *  1. For each day, work out the fewest trips needed to ship any subset of
 *     cargo that evening (a subset DP over that day's trucks), asking every
 *     TripRule whether a truck may take a load.
 *  2. Walk the days with a DP over "shipped so far" masks. Each morning the
 *     warehouse check must hold. Keep the best (score, then fewest trips).
 *
 * The planner keeps no state between calls, so one object can be reused.
 */
public class RecoveryPlanner {
    private static final int NO_WAY = Integer.MAX_VALUE / 4;

    private final List<TripRule> rules;

    /** A planner with the standard rules. */
    public RecoveryPlanner() {
        this(defaultRules());
    }

    /** A planner with a custom list of trip rules (the list is copied). */
    public RecoveryPlanner(List<TripRule> rules) {
        this.rules = new ArrayList<>(rules);
    }

    /** The standard restrictions: availability, arrival, capacity and hazmat. */
    public static List<TripRule> defaultRules() {
        List<TripRule> list = new ArrayList<>();
        list.add(new TruckAvailableRule());
        list.add(new ArrivalRule());
        list.add(new TruckCapacityRule());
        list.add(new HazmatCertifiedRule());
        list.add(new HazmatConflictRule());
        return list;
    }

    /** Adds a new restriction. Future plans respect it. */
    public void addRule(TripRule rule) {
        if (rule != null) {
            rules.add(rule);
        }
    }

    /**
     * Returns the best plan for this scenario, or null if no valid plan exists.
     */
    public Plan plan(Scenario scenario) {
        if (scenario == null) {
            return null;
        }
        List<RecoveryCargo> cargo = scenario.getCargo();
        List<Truck> trucks = scenario.getTrucks();
        int days = scenario.getHorizonDays();
        int n = cargo.size();
        int full = (1 << n) - 1;
        long baseWeight = scenario.getWarehouse().getCurrentWeight();
        int capacity = scenario.getWarehouse().getCapacity();

        long[] weight = new long[full + 1];
        for (int mask = 1; mask <= full; mask++) {
            int low = Integer.numberOfTrailingZeros(mask);
            weight[mask] = weight[mask & (mask - 1)] + cargo.get(low).getWeight();
        }

        if (days < 1) {
            return baseWeight <= capacity ? new Plan() : null;
        }

        // Per day: fewest trips for each subset, plus how each truck was loaded.
        int[][] trips = new int[days + 1][];
        int[][][] truckLoad = new int[days + 1][][];
        int[] arrived = new int[days + 1];
        for (int day = 1; day <= days; day++) {
            for (int i = 0; i < n; i++) {
                if (cargo.get(i).getInboundDay() <= day) {
                    arrived[day] |= 1 << i;
                }
            }
            truckLoad[day] = new int[trucks.size()][full + 1];
            trips[day] = packDay(scenario, cargo, trucks, day, arrived[day], truckLoad[day]);
        }

        // DP over days. score/tripCount for "shipped mask after this day"; -1 = unreachable.
        int[] score = new int[full + 1];
        int[] tripCount = new int[full + 1];
        Arrays.fill(score, -1);
        score[0] = 0;
        int[][] parent = new int[days + 1][full + 1];

        for (int day = 1; day <= days; day++) {
            int[] nextScore = new int[full + 1];
            int[] nextTrips = new int[full + 1];
            Arrays.fill(nextScore, -1);
            for (int mask = 0; mask <= full; mask++) {
                if (score[mask] < 0) {
                    continue;
                }
                int waiting = arrived[day] & ~mask;
                if (baseWeight + weight[waiting] > capacity) {
                    continue; // Morning check fails: this history is not valid.
                }
                for (int sub = waiting; ; sub = (sub - 1) & waiting) {
                    if (trips[day][sub] < NO_WAY) {
                        int newMask = mask | sub;
                        int newScore = score[mask] + onTimeScore(cargo, sub, day);
                        int newTrips = tripCount[mask] + trips[day][sub];
                        if (better(newScore, newTrips, nextScore[newMask], nextTrips[newMask])) {
                            nextScore[newMask] = newScore;
                            nextTrips[newMask] = newTrips;
                            parent[day][newMask] = mask;
                        }
                    }
                    if (sub == 0) {
                        break;
                    }
                }
            }
            score = nextScore;
            tripCount = nextTrips;
        }

        int bestMask = -1;
        for (int mask = 0; mask <= full; mask++) {
            if (score[mask] >= 0 && (bestMask < 0 || better(score[mask], tripCount[mask], score[bestMask], tripCount[bestMask]))) {
                bestMask = mask;
            }
        }
        if (bestMask < 0) {
            return null;
        }

        Plan plan = new Plan();
        int mask = bestMask;
        for (int day = days; day >= 1; day--) {
            int before = parent[day][mask];
            int shippedToday = mask ^ before;
            addTrips(plan, cargo, trucks, day, shippedToday, truckLoad[day]);
            mask = before;
        }
        return plan;
    }

    /**
     * Fewest trips to ship each subset of the arrived cargo on one day.
     * layer k = using only the first k trucks; load[k][S] = what truck k carries in the best way.
     */
    private int[] packDay(Scenario scenario, List<RecoveryCargo> cargo, List<Truck> trucks,
            int day, int arrived, int[][] load) {
        int full = (1 << cargo.size()) - 1;
        int[] best = new int[full + 1];
        Arrays.fill(best, NO_WAY);
        best[0] = 0;
        for (int t = 0; t < trucks.size(); t++) {
            Truck truck = trucks.get(t);
            boolean[] allowed = new boolean[full + 1];
            boolean any = false;
            for (int sub = arrived; sub != 0; sub = (sub - 1) & arrived) {
                allowed[sub] = allowsTrip(scenario, truck, day, toList(cargo, sub));
                any |= allowed[sub];
            }
            if (!any) {
                continue; // This truck cannot help today; load[t] stays all zero.
            }
            int[] next = best.clone();
            for (int set = arrived; ; set = (set - 1) & arrived) {
                for (int sub = set; sub != 0; sub = (sub - 1) & set) {
                    if (allowed[sub] && best[set ^ sub] + 1 < next[set]) {
                        next[set] = best[set ^ sub] + 1;
                        load[t][set] = sub;
                    }
                }
                if (set == 0) {
                    break;
                }
            }
            best = next;
        }
        return best;
    }

    /** Rebuilds the trips for one day by walking the truck layers backwards. */
    private void addTrips(Plan plan, List<RecoveryCargo> cargo, List<Truck> trucks,
            int day, int set, int[][] load) {
        for (int t = trucks.size() - 1; t >= 0 && set != 0; t--) {
            int sub = load[t][set];
            if (sub != 0) {
                for (RecoveryCargo c : toList(cargo, sub)) {
                    plan.add(new Assignment(c, trucks.get(t), day));
                }
                set ^= sub;
            }
        }
    }

    private boolean allowsTrip(Scenario scenario, Truck truck, int day, List<RecoveryCargo> load) {
        for (TripRule rule : rules) {
            if (!rule.allows(scenario, truck, day, load)) {
                return false;
            }
        }
        return true;
    }

    private static int onTimeScore(List<RecoveryCargo> cargo, int set, int day) {
        int total = 0;
        for (int i = 0; i < cargo.size(); i++) {
            if ((set & (1 << i)) != 0 && day <= cargo.get(i).getDeadlineDay()) {
                total += cargo.get(i).getPriority();
            }
        }
        return total;
    }

    private static boolean better(int score, int trips, int otherScore, int otherTrips) {
        return score > otherScore || (score == otherScore && trips < otherTrips);
    }

    private static List<RecoveryCargo> toList(List<RecoveryCargo> cargo, int set) {
        List<RecoveryCargo> list = new ArrayList<>();
        for (int i = 0; i < cargo.size(); i++) {
            if ((set & (1 << i)) != 0) {
                list.add(cargo.get(i));
            }
        }
        return list;
    }
}
