package org.amped.model.recovery;

import java.util.ArrayList;

/**
 * Part 4 (optional final boss): a plan is a list of assignments.
 * Cargo that is not in the plan simply stays in the warehouse.
 *
 * The score and the trip count are worked out from the assignments, so you
 * can use them to compare two plans.
 */
public class Plan {
    private ArrayList<Assignment> assignments;

    /** Creates an empty plan (it ships nothing). */
    public Plan() {
        this.assignments = new ArrayList<>();
    }

    /** Adds one assignment to the plan. A null assignment is ignored. */
    public void add(Assignment assignment) {
        if (assignment == null) {
            return;
        }
        assignments.add(assignment);
    }

    /** Returns a copy of the list of assignments (the assignment objects are the same). */
    public ArrayList<Assignment> getAssignments() {
        return new ArrayList<>(assignments);
    }

    /**
     * Returns the score: the sum of the priority of every assignment that is
     * on time (its day is not after the cargo's deadline day). Late cargo
     * gives 0 points.
     */
    public int getScore() {
        int score = 0;
        for (Assignment assignment : assignments) {
            if (assignment.getDay() <= assignment.getCargo().getDeadlineDay()) {
                score = score + assignment.getCargo().getPriority();
            }
        }
        return score;
    }

    /**
     * Returns the number of trips: how many different (truck, day) pairs the
     * plan uses. Two cargo on the same truck on the same day share one trip.
     */
    public int getTripCount() {
        int trips = 0;
        for (int i = 0; i < assignments.size(); i++) {
            Assignment current = assignments.get(i);
            // Count this assignment only if no earlier one uses the same trip.
            boolean seenBefore = false;
            for (int j = 0; j < i; j++) {
                Assignment earlier = assignments.get(j);
                if (earlier.getTruck() == current.getTruck() && earlier.getDay() == current.getDay()) {
                    seenBefore = true;
                }
            }
            if (!seenBefore) {
                trips++;
            }
        }
        return trips;
    }
}
