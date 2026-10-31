package org.amped.model;

/**
 * One kind of cargo, for example "A" or "BOLT-5".
 * It has a SKU (its name tag) and the weight of ONE unit.
 */
public class Cargo {
    private String sku;
    private int unitWeight;

    /**
     * @param sku        the name tag of this cargo, like "A"
     * @param unitWeight the weight of one unit
     */
    public Cargo(String sku, int unitWeight) {
        this.sku = sku;
        this.unitWeight = unitWeight;
    }

    /** Returns the SKU (name tag) of this cargo. */
    public String getSku() {
        return sku;
    }

    /** Returns the weight of one unit. */
    public int getUnitWeight() {
        return unitWeight;
    }
}
