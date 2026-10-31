package org.amped.app;

import java.util.ArrayList;
import java.util.List;
import org.amped.model.Cargo;
import org.amped.model.Shipment;
import org.amped.model.Warehouse;
import org.amped.service.CargoManager;

/**
 * Visible tests for CargoManager. Run this file to see PASS or FAIL for each test.
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
