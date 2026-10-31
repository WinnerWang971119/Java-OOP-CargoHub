package org.amped.model.recovery;

/**
 * Part 4 (optional final boss): one piece of cargo to plan.
 * It is a single piece. It ships once and is never split.
 *
 * It arrives in the morning of its inbound day. A cargo is on time if it
 * ships on its deadline day (in the evening) or earlier.
 */
public class RecoveryCargo {
    private String id;
    private int weight;
    private int priority;
    private int inboundDay;
    private int deadlineDay;
    private String hazmatClass;

    /**
     * @param id          the name of this cargo, like "A"
     * @param weight      the weight of the whole piece (1 or more)
     * @param priority    how much it is worth if it ships on time (1 or more)
     * @param inboundDay  the day it arrives, in the morning (1 to the horizon)
     * @param deadlineDay the last day it can ship on time (inboundDay to the horizon)
     * @param hazmatClass the hazmat class, or "NONE" if it is not hazardous
     *                    (null is treated as "NONE")
     */
    public RecoveryCargo(String id, int weight, int priority, int inboundDay,
            int deadlineDay, String hazmatClass) {
        this.id = id;
        this.weight = weight;
        this.priority = priority;
        this.inboundDay = inboundDay;
        this.deadlineDay = deadlineDay;
        if (hazmatClass == null) {
            this.hazmatClass = "NONE";
        } else {
            this.hazmatClass = hazmatClass;
        }
    }

    /** Returns the name of this cargo. */
    public String getId() {
        return id;
    }

    /** Returns the weight of the whole piece. */
    public int getWeight() {
        return weight;
    }

    /** Returns the points this cargo gives if it ships on time. */
    public int getPriority() {
        return priority;
    }

    /** Returns the day this cargo arrives (in the morning). */
    public int getInboundDay() {
        return inboundDay;
    }

    /** Returns the last day this cargo can ship on time (that evening or earlier). */
    public int getDeadlineDay() {
        return deadlineDay;
    }

    /** Returns the hazmat class. "NONE" means the cargo is not hazardous. */
    public String getHazmatClass() {
        return hazmatClass;
    }

    /** Returns true if this cargo is hazardous (its class is not "NONE"). */
    public boolean isHazmat() {
        return !"NONE".equals(hazmatClass);
    }
}
