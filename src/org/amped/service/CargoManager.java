package org.amped.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.amped.model.Cargo;
import org.amped.model.Shipment;
import org.amped.model.Warehouse;

/**
 * The brain of CargoHub: applies batches, picks inbound shipments and loads trucks.
 */
public class CargoManager {
    private Warehouse warehouse;

    public CargoManager(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    /**
     * Part 1: apply a whole batch, all or nothing.
     * Checks every SKU's net change first and only then touches the warehouse.
     */
    public boolean processBatch(List<Shipment> shipments) {
        if (shipments == null || shipments.isEmpty()) {
            return true;
        }
        Map<String, Integer> inbound = new LinkedHashMap<>();
        Map<String, Integer> outbound = new LinkedHashMap<>();
        Map<String, Cargo> cargoBySku = new HashMap<>();

        for (Shipment s : shipments) {
            String sku = s.getSku();
            Map<String, Integer> side = s.isInbound() ? inbound : outbound;
            side.merge(sku, s.getQuantity(), Integer::sum);
            if (!cargoBySku.containsKey(sku)) {
                // The warehouse's own Cargo decides the unit weight once it knows the SKU.
                Cargo known = warehouse.getCargo(sku);
                cargoBySku.put(sku, known != null ? known : s.getCargo());
            }
        }

        long weightAfter = warehouse.getCurrentWeight();
        for (Map.Entry<String, Cargo> entry : cargoBySku.entrySet()) {
            String sku = entry.getKey();
            long net = (long) inbound.getOrDefault(sku, 0) - outbound.getOrDefault(sku, 0);
            if (warehouse.getStock(sku) + net < 0) {
                return false;
            }
            weightAfter += net * entry.getValue().getUnitWeight();
        }
        if (weightAfter > warehouse.getCapacity()) {
            return false;
        }

        // Valid: add the inbound first so outbound of a brand-new SKU always succeeds.
        for (Map.Entry<String, Integer> entry : inbound.entrySet()) {
            warehouse.addStock(cargoBySku.get(entry.getKey()), entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : outbound.entrySet()) {
            warehouse.removeStock(entry.getKey(), entry.getValue());
        }
        return true;
    }

    /**
     * Part 2: 0/1 knapsack. Budget = free capacity, value = priority.
     * dp[i][w] = best priority using the first i candidates within weight w.
     */
    public List<Shipment> optimizeInbound(List<Shipment> candidates) {
        List<Shipment> result = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) {
            return result;
        }
        int budget = warehouse.getFreeCapacity();
        if (budget <= 0) {
            return result;
        }
        List<Shipment> items = new ArrayList<>();
        for (Shipment s : candidates) {
            if (s != null && s.isInbound() && s.getWeight() <= budget && !containsSame(items, s)) {
                items.add(s);
            }
        }
        int n = items.size();
        long[][] dp = new long[n + 1][budget + 1];
        for (int i = 1; i <= n; i++) {
            int weight = items.get(i - 1).getWeight();
            int priority = items.get(i - 1).getPriority();
            for (int w = 0; w <= budget; w++) {
                dp[i][w] = dp[i - 1][w];
                if (weight <= w && dp[i - 1][w - weight] + priority > dp[i][w]) {
                    dp[i][w] = dp[i - 1][w - weight] + priority;
                }
            }
        }
        // Walk back to find which items were taken.
        int w = budget;
        for (int i = n; i >= 1; i--) {
            if (dp[i][w] != dp[i - 1][w]) {
                result.add(items.get(i - 1));
                w -= items.get(i - 1).getWeight();
            }
        }
        return result;
    }

    private static boolean containsSame(List<Shipment> list, Shipment s) {
        for (Shipment other : list) {
            if (other == s) {
                return true;
            }
        }
        return false;
    }

    /**
     * Part 3: bin packing with conflicts, exact.
     * 1. ok[mask] = this group of shipments fits on one truck with no conflict.
     * 2. best[mask] = fewest trucks for this group, trying every valid truck that holds
     *    the lowest shipment still left (so each split is counted once).
     */
    public List<List<Shipment>> planTrucks(List<Shipment> shipments, int truckCapacity, List<String[]> conflicts) {
        if (shipments == null || shipments.isEmpty()) {
            return new ArrayList<>();
        }
        int n = shipments.size();
        for (Shipment s : shipments) {
            if (s.getWeight() > truckCapacity) {
                return null;
            }
        }

        // clash[i] = bitmask of shipments that may not ride with shipment i.
        int[] clash = new int[n];
        if (conflicts != null) {
            for (String[] pair : conflicts) {
                if (pair == null || pair.length < 2 || pair[0].equals(pair[1])) {
                    continue;
                }
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        String a = shipments.get(i).getSku();
                        String b = shipments.get(j).getSku();
                        if ((a.equals(pair[0]) && b.equals(pair[1])) || (a.equals(pair[1]) && b.equals(pair[0]))) {
                            clash[i] |= 1 << j;
                            clash[j] |= 1 << i;
                        }
                    }
                }
            }
        }

        int full = (1 << n) - 1;
        boolean[] ok = new boolean[full + 1];
        long[] weight = new long[full + 1];
        ok[0] = true;
        for (int mask = 1; mask <= full; mask++) {
            int low = Integer.numberOfTrailingZeros(mask);
            int rest = mask & (mask - 1);
            weight[mask] = weight[rest] + shipments.get(low).getWeight();
            ok[mask] = ok[rest] && weight[mask] <= truckCapacity && (clash[low] & rest) == 0;
        }

        int[] best = new int[full + 1];
        int[] pick = new int[full + 1];
        best[0] = 0;
        for (int mask = 1; mask <= full; mask++) {
            best[mask] = Integer.MAX_VALUE;
            int low = mask & -mask;
            int others = mask ^ low;
            // Every submask of "others", plus the lowest bit, is a candidate truck.
            for (int sub = others; ; sub = (sub - 1) & others) {
                int truck = sub | low;
                if (ok[truck] && best[mask ^ truck] != Integer.MAX_VALUE && best[mask ^ truck] + 1 < best[mask]) {
                    best[mask] = best[mask ^ truck] + 1;
                    pick[mask] = truck;
                }
                if (sub == 0) {
                    break;
                }
            }
        }

        List<List<Shipment>> trucks = new ArrayList<>();
        for (int mask = full; mask != 0; mask ^= pick[mask]) {
            List<Shipment> load = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((pick[mask] & (1 << i)) != 0) {
                    load.add(shipments.get(i));
                }
            }
            trucks.add(load);
        }
        return trucks;
    }
}
