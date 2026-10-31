package org.amped.app;

import java.util.ArrayList;
import java.util.List;
import org.amped.model.Cargo;
import org.amped.model.Shipment;
import org.amped.model.Warehouse;
import org.amped.service.CargoManager;

/**
 * A small demo: build a warehouse, run one batch, and look at the result.
 * Run it to see what your CargoManager does.
 */
public class Main {
    public static void main(String[] args) {
        // A warehouse with room for weight 100, holding 5 units of A (weight 2 each).
        Warehouse warehouse = new Warehouse(100);
        Cargo cargoA = new Cargo("A", 2);
        warehouse.addStock(cargoA, 5);
        CargoManager manager = new CargoManager(warehouse);

        printWarehouse("Before", manager.getWarehouse());

        // One batch: 7 units of A go out, 4 units of A come in.
        // Net = -3, so a finished processBatch accepts it and A drops to 2 units.
        List<Shipment> batch = new ArrayList<>();
        batch.add(new Shipment(cargoA, 7, false));
        batch.add(new Shipment(cargoA, 4, true));
        boolean accepted = manager.processBatch(batch);
        System.out.println("processBatch returned: " + accepted);

        printWarehouse("After", manager.getWarehouse());
    }

    // Prints the capacity, the weight, and the stock of every SKU.
    private static void printWarehouse(String label, Warehouse warehouse) {
        System.out.println(label + ": capacity " + warehouse.getCapacity()
                + ", weight " + warehouse.getCurrentWeight()
                + ", free " + warehouse.getFreeCapacity());
        for (String sku : warehouse.getSkus()) {
            System.out.println("  " + sku + ": " + warehouse.getStock(sku) + " units");
        }
    }
}
