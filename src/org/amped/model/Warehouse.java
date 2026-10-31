package org.amped.model;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A warehouse with a weight capacity. It remembers every cargo type it has
 * seen and how many units of each one are in stock.
 *
 * Careful: addStock does NOT check the capacity. Making sure a change fits
 * is the job of your CargoManager.
 */
public class Warehouse {
    private int capacity;
    private HashMap<String, Cargo> cargoBySku;
    private HashMap<String, Integer> stockBySku;
    private ArrayList<String> skus;

    /** Creates an empty warehouse that can hold this much weight. */
    public Warehouse(int capacity) {
        this.capacity = capacity;
        this.cargoBySku = new HashMap<>();
        this.stockBySku = new HashMap<>();
        this.skus = new ArrayList<>();
    }

    /** Returns the maximum total weight the warehouse can hold. */
    public int getCapacity() {
        return capacity;
    }

    /** Returns true if this SKU has ever been added to the warehouse. */
    public boolean hasCargo(String sku) {
        return cargoBySku.containsKey(sku);
    }

    /** Returns the Cargo for this SKU, or null if the warehouse never saw it. */
    public Cargo getCargo(String sku) {
        return cargoBySku.get(sku);
    }

    /** Returns how many units of this SKU are in stock (0 if unknown). */
    public int getStock(String sku) {
        if (!stockBySku.containsKey(sku)) {
            return 0;
        }
        return stockBySku.get(sku);
    }

    /** Returns the total weight in stock: the sum of (stock * unit weight). */
    public int getCurrentWeight() {
        int total = 0;
        for (String sku : skus) {
            total = total + stockBySku.get(sku) * cargoBySku.get(sku).getUnitWeight();
        }
        return total;
    }

    /** Returns how much more weight fits: capacity - current weight. */
    public int getFreeCapacity() {
        return capacity - getCurrentWeight();
    }

    /**
     * Adds units to the stock. If the cargo is new, the warehouse registers
     * it first. A negative quantity is ignored.
     * No capacity check here.
     */
    public void addStock(Cargo cargo, int quantity) {
        String sku = cargo.getSku();
        if (quantity < 0) {
            return;
        }
        if (!cargoBySku.containsKey(sku)) {
            cargoBySku.put(sku, cargo);
            stockBySku.put(sku, 0);
            skus.add(sku);
        }
        stockBySku.put(sku, stockBySku.get(sku) + quantity);
    }

    /**
     * Takes units out of the stock.
     * Returns true if it worked. Returns false, and changes nothing, if
     * there are fewer units than requested (or the quantity is negative).
     */
    public boolean removeStock(String sku, int quantity) {
        if (quantity < 0 || getStock(sku) < quantity) {
            return false;
        }
        if (quantity > 0) {
            stockBySku.put(sku, getStock(sku) - quantity);
        }
        return true;
    }

    /** Returns a copy of the list of registered SKUs, in the order they were added. */
    public ArrayList<String> getSkus() {
        return new ArrayList<>(skus);
    }
}
