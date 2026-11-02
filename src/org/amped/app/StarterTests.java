package org.amped.app;

import java.util.ArrayList;
import java.util.List;
import org.amped.model.Cargo;
import org.amped.model.Shipment;
import org.amped.model.Warehouse;
import org.amped.model.recovery.Plan;
import org.amped.model.recovery.RecoveryCargo;
import org.amped.model.recovery.Scenario;
import org.amped.model.recovery.Truck;
import org.amped.service.CargoManager;
import org.amped.service.RecoveryPlanner;

/**
 * Visible tests for CargoManager (and one for the optional RecoveryPlanner).
 * Run this file to see PASS or FAIL for each test.
 * These are only a few examples. The real check on your pull request has many more.
 * Your own tests are welcome: add them here or in a new file.
 */
public class StarterTests {
    public static void main(String[] args) {
        int passed = 0;
        int total = 0;

        Warehouse warehouse;
        CargoManager manager;
        List<Shipment> batch;
        boolean ok;

        // Part 1, test 1: the example from the slides.
        // Capacity 100, A weighs 2, stock 5. Out 7 and in 4: net -3, so stock 2.
        warehouse = new Warehouse(100);
        Cargo cargoA = new Cargo("A", 2);
        warehouse.addStock(cargoA, 5);
        manager = new CargoManager(warehouse);
        batch = new ArrayList<>();
        batch.add(new Shipment(cargoA, 7, false));
        batch.add(new Shipment(cargoA, 4, true));
        ok = manager.processBatch(batch) && warehouse.getStock("A") == 2;
        total++;
        passed += report("Part 1: out 7, in 4 -> accepted, stock 2", ok);

        // Part 1, test 2: a rejected batch changes nothing.
        // Line 2 ships out 6 A but only 5 are in stock, so the whole batch is rejected.
        // B (line 1) must not show up in the warehouse.
        warehouse = new Warehouse(100);
        warehouse.addStock(cargoA, 5);
        Cargo cargoB = new Cargo("B", 3);
        manager = new CargoManager(warehouse);
        batch = new ArrayList<>();
        batch.add(new Shipment(cargoB, 10, true));
        batch.add(new Shipment(cargoA, 6, false));
        ok = !manager.processBatch(batch)
                && warehouse.getStock("A") == 5
                && warehouse.getStock("B") == 0
                && !warehouse.hasCargo("B")
                && warehouse.getCurrentWeight() == 10;
        total++;
        passed += report("Part 1: rejected batch changes nothing", ok);

        // Part 1, test 3: an empty batch is accepted and changes nothing.
        warehouse = new Warehouse(100);
        warehouse.addStock(cargoA, 5);
        manager = new CargoManager(warehouse);
        batch = new ArrayList<>();
        ok = manager.processBatch(batch)
                && warehouse.getStock("A") == 5
                && warehouse.getCurrentWeight() == 10;
        total++;
        passed += report("Part 1: empty batch -> accepted, nothing changes", ok);

        // Part 2, test 1: the greedy trap.
        // Free capacity 10. A (weight 6, priority 12), B (5, 10), C (5, 10).
        // Taking A first blocks both others (total 12). B + C fit and give 20.
        warehouse = new Warehouse(10);
        manager = new CargoManager(warehouse);
        List<Shipment> candidates = new ArrayList<>();
        candidates.add(new Shipment(new Cargo("A", 6), 1, true, 12));
        candidates.add(new Shipment(new Cargo("B", 5), 1, true, 10));
        candidates.add(new Shipment(new Cargo("C", 5), 1, true, 10));
        List<Shipment> chosen = manager.optimizeInbound(candidates);
        ok = false;
        if (chosen != null) {
            int chosenWeight = 0;
            int chosenPriority = 0;
            for (Shipment shipment : chosen) {
                chosenWeight += shipment.getWeight();
                chosenPriority += shipment.getPriority();
            }
            ok = chosenPriority == 20 && chosenWeight <= warehouse.getFreeCapacity();
        }
        total++;
        passed += report("Part 2: greedy trap -> total priority 20, fits", ok);

        // Part 2, test 2: no candidates gives an empty list (not null).
        chosen = manager.optimizeInbound(new ArrayList<Shipment>());
        ok = chosen != null && chosen.isEmpty();
        total++;
        passed += report("Part 2: no candidates -> empty list", ok);

        // Part 3 (optional bonus): truck limit 10, weights 4, 4, 3, 3, 3, 3.
        // "Biggest first" needs 3 trucks. The best plan (4+3+3 twice) needs 2.
        int[] weights = {4, 4, 3, 3, 3, 3};
        List<Shipment> shipments = new ArrayList<>();
        for (int i = 0; i < weights.length; i++) {
            Cargo cargo = new Cargo("S" + i, 1);
            shipments.add(new Shipment(cargo, weights[i], false));
        }
        List<List<Shipment>> trucks = manager.planTrucks(shipments, 10, new ArrayList<String[]>());
        ok = false;
        if (trucks != null && trucks.size() == 2) {
            int loaded = 0;
            ok = true;
            for (List<Shipment> truck : trucks) {
                int truckWeight = 0;
                for (Shipment shipment : truck) {
                    truckWeight += shipment.getWeight();
                    loaded++;
                }
                if (truckWeight > 10) {
                    ok = false;
                }
            }
            if (loaded != shipments.size()) {
                ok = false;
            }
        }
        total++;
        passed += report("Part 3 (bonus): limit 10, 6 shipments -> 2 trucks", ok);

        // Part 4 (optional final boss): 2 days, one truck of capacity 10 that can leave
        // on day 1 and day 2. A (weight 6, priority 5) must ship by day 1.
        // B (weight 6, priority 3) must ship by day 2. They cannot share a trip
        // (6 + 6 > 10), so: A on day 1, B on day 2 = score 8 with 2 trips.
        // (B on day 1 and A on day 2 would make A late: only 3 points.)
        Warehouse recoveryWarehouse = new Warehouse(100);
        ArrayList<Integer> truckDays = new ArrayList<>();
        truckDays.add(1);
        truckDays.add(2);
        ArrayList<Truck> recoveryTrucks = new ArrayList<>();
        recoveryTrucks.add(new Truck("T1", 10, false, truckDays));
        ArrayList<RecoveryCargo> recoveryCargo = new ArrayList<>();
        recoveryCargo.add(new RecoveryCargo("A", 6, 5, 1, 1, "NONE"));
        recoveryCargo.add(new RecoveryCargo("B", 6, 3, 1, 2, "NONE"));
        Scenario scenario = new Scenario(recoveryWarehouse, 2, recoveryCargo,
                recoveryTrucks, new ArrayList<String[]>());
        RecoveryPlanner planner = new RecoveryPlanner();
        Plan plan = planner.plan(scenario);
        ok = plan != null && plan.getScore() == 8 && plan.getTripCount() == 2;
        total++;
        passed += report("Part 4 (final boss): 2 days, 1 truck -> score 8, 2 trips", ok);


        // ---- Extra tests ----

        // Part 1: over capacity is rejected and changes nothing.
        warehouse = new Warehouse(20);
        manager = new CargoManager(warehouse);
        batch = new ArrayList<>();
        batch.add(new Shipment(new Cargo("H", 7), 3, true));
        ok = !manager.processBatch(batch) && warehouse.getCurrentWeight() == 0 && !warehouse.hasCargo("H");
        total++;
        passed += report("Part 1: 21 > capacity 20 -> rejected", ok);

        // Part 1: exactly full is fine, and a new SKU can go in and out in one batch.
        warehouse = new Warehouse(20);
        manager = new CargoManager(warehouse);
        Cargo cargoN = new Cargo("N", 4);
        batch = new ArrayList<>();
        batch.add(new Shipment(cargoN, 2, false));
        batch.add(new Shipment(cargoN, 7, true));
        ok = manager.processBatch(batch) && warehouse.getStock("N") == 5 && warehouse.getFreeCapacity() == 0;
        total++;
        passed += report("Part 1: new SKU in 7 out 2, exactly full -> accepted", ok);

        // Part 2: the warehouse is never changed and nothing too heavy is picked.
        warehouse = new Warehouse(5);
        manager = new CargoManager(warehouse);
        candidates = new ArrayList<>();
        candidates.add(new Shipment(new Cargo("X", 6), 1, true, 100));
        candidates.add(new Shipment(new Cargo("Y", 2), 2, true, 3));
        List<Shipment> picked = manager.optimizeInbound(candidates);
        ok = picked.size() == 1 && picked.get(0) == candidates.get(1) && warehouse.getCurrentWeight() == 0;
        total++;
        passed += report("Part 2: too-heavy candidate skipped, warehouse unchanged", ok);

        // Part 3: conflicting SKUs never share a truck; a too-heavy shipment gives null.
        manager = new CargoManager(new Warehouse(100));
        List<Shipment> hazmat = new ArrayList<>();
        hazmat.add(new Shipment(new Cargo("F", 1), 1, true));
        hazmat.add(new Shipment(new Cargo("O", 1), 1, true));
        hazmat.add(new Shipment(new Cargo("F", 1), 1, true));
        List<String[]> pairs = new ArrayList<>();
        pairs.add(new String[] {"F", "O"});
        List<List<Shipment>> loaded = manager.planTrucks(hazmat, 10, pairs);
        List<Shipment> heavy = new ArrayList<>();
        heavy.add(new Shipment(new Cargo("Q", 11), 1, true));
        ok = loaded != null && loaded.size() == 2 && manager.planTrucks(heavy, 10, pairs) == null;
        total++;
        passed += report("Part 3: F/O conflict -> 2 trucks; too heavy -> null", ok);

        // Part 4: the warehouse overflows on day 2 unless something ships on day 1.
        // Warehouse 10. A (8) arrives day 1, B (8) arrives day 2. Truck only on day 1.
        // A must leave day 1 (even though it scores the same), or day 2 morning is 16 > 10.
        ArrayList<Integer> dayOne = new ArrayList<>();
        dayOne.add(1);
        ArrayList<Truck> oneTruck = new ArrayList<>();
        oneTruck.add(new Truck("T", 10, false, dayOne));
        ArrayList<RecoveryCargo> overflow = new ArrayList<>();
        overflow.add(new RecoveryCargo("A", 8, 1, 1, 2, "NONE"));
        overflow.add(new RecoveryCargo("B", 8, 1, 2, 2, "NONE"));
        Warehouse small = new Warehouse(10);
        plan = planner.plan(new Scenario(small, 2, overflow, oneTruck, null));
        ok = plan != null && plan.getScore() == 1 && plan.getTripCount() == 1 && small.getCurrentWeight() == 0;
        total++;
        passed += report("Part 4: must ship A on day 1 to make room", ok);

        // Part 4: hazmat cargo on an uncertified truck is impossible, so it stays.
        ArrayList<RecoveryCargo> acid = new ArrayList<>();
        acid.add(new RecoveryCargo("ACID", 1, 9, 1, 1, "CORROSIVE"));
        plan = planner.plan(new Scenario(new Warehouse(10), 1, acid, oneTruck, null));
        ok = plan != null && plan.getScore() == 0 && plan.getTripCount() == 0;
        total++;
        passed += report("Part 4: hazmat needs a certified truck", ok);

        // Part 4: no valid plan when the first morning already overflows.
        ArrayList<RecoveryCargo> huge = new ArrayList<>();
        huge.add(new RecoveryCargo("BIG", 50, 1, 1, 1, "NONE"));
        ok = planner.plan(new Scenario(new Warehouse(10), 1, huge, oneTruck, null)) == null;
        total++;
        passed += report("Part 4: impossible morning -> null", ok);

        // Part 4: a new rule plugs in without changing the planner.
        RecoveryPlanner strict = new RecoveryPlanner();
        strict.addRule((sc, truck, day, load) -> load.size() <= 1);
        plan = strict.plan(scenario);
        ok = plan != null && plan.getScore() == 8 && plan.getTripCount() == 2;
        total++;
        passed += report("Part 4: extra rule (one cargo per trip) still plans", ok);

        System.out.println();
        System.out.println(passed + "/" + total + " passed");
    }

    // Prints PASS or FAIL for one test. Returns 1 if it passed, 0 if not.
    private static int report(String name, boolean ok) {
        if (ok) {
            System.out.println("PASS  " + name);
            return 1;
        }
        System.out.println("FAIL  " + name);
        return 0;
    }
}
