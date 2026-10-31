# Operation Recovery (final boss)

Optional. This is the final boss, much harder than Parts 1 to 3. Start it when Parts 1 and 2 are done and you want a real challenge.

## The story
Your cargo system shipped. Then a storm hit. Some trucks are down, shipments are delayed, and urgent deliveries have deadlines.

Design a recovery planner that maximises successful deliveries while respecting warehouse capacity, transport limits and hazmat rules.

Your job: write `plan(Scenario)` in `src/org/amped/service/RecoveryPlanner.java`. It returns the best `Plan`.

## How a day works
| Time | What happens |
|------|--------------|
| Morning | New cargo arrives at the warehouse |
| Evening | Trucks leave with the cargo you picked |

You plan 1 to 3 days, numbered 1, 2, 3. Cargo you never ship just stays in the warehouse.

## The classes
The first five are provided in `src/org/amped/model/recovery/`. Read them, do not change them (changes are ignored).

| Class | What it is | Methods you use |
|-------|------------|-----------------|
| `RecoveryCargo` | One piece of cargo. Ships once, never split | `getId()`, `getWeight()`, `getPriority()`, `getInboundDay()`, `getDeadlineDay()`, `getHazmatClass()`, `isHazmat()` |
| `Truck` | One truck | `getId()`, `getCapacity()`, `isHazmatCertified()`, `isAvailableOn(day)`, `getAvailableDays()` |
| `Scenario` | Everything you plan from | `getWarehouse()`, `getHorizonDays()`, `getCargo()`, `getTrucks()`, `getHazmatConflicts()` |
| `Assignment` | One decision: this cargo, on this truck, on this day | `new Assignment(cargo, truck, day)`, `getCargo()`, `getTruck()`, `getDay()` |
| `Plan` | Your answer: a list of assignments | `new Plan()`, `add(assignment)`, `getAssignments()`, `getScore()`, `getTripCount()` |
| `RecoveryPlanner` | You write this one, in `service/` | `plan(Scenario)` |

The lists from `Scenario` are copies, but the cargo, trucks and warehouse inside are the real ones. Do not change them.
You may add your own classes next to `RecoveryPlanner` in `org.amped.service`.

## The objective
1. Most priority delivered on time (the score).
2. On a tie, the fewest trips. One trip is one truck on one evening.

`Plan` works both out for you: `getScore()` and `getTripCount()`.

## The constraints
- Each cargo ships at most once, in one piece.
- Cargo never ships before its inbound day (it arrives in the morning and can leave that evening) or after the last planned day.
- A truck only leaves on a day it is available (`truck.isAvailableOn(day)`).
- On every trip, the total weight is not above the truck's capacity.
- Hazmat cargo (class not `"NONE"`) only goes on a hazmat-certified truck.
- Two cargo whose hazmat classes are a conflict pair never share a trip. A class never conflicts with itself.
- Every morning, after the new cargo arrives and before any truck leaves: warehouse current weight plus all cargo that arrived and has not shipped yet is not above the warehouse capacity. Exactly equal is fine.
- Late shipping is allowed, but it scores 0. It can still help, because it frees space.
- No valid plan exists? Return `null`. An empty plan is valid when the capacity holds every morning.

## Limits
| What | Limit |
|------|-------|
| Cargo | up to 12 |
| Trucks | up to 4 |
| Days | up to 3 |
| Warehouse capacity | up to 1000 |
| Weights and priorities | whole numbers, 1 or more |
| Time per call | 5 seconds |

## A tiny example
2 days. Warehouse capacity 100, empty. Truck T1: capacity 10, not hazmat certified, available on days 1 and 2.

| Cargo | Weight | Priority | Arrives | Deadline | Hazmat |
|-------|--------|----------|---------|----------|--------|
| A | 6 | 5 | day 1 | day 1 | NONE |
| B | 6 | 3 | day 1 | day 2 | NONE |

| Plan | Score | Trips | Why |
|------|-------|-------|-----|
| A on day 1, B on day 2 | 8 | 2 | Both on time. This is the best plan |
| B on day 1, A on day 2 | 3 | 2 | A is late, so it scores 0 |
| A and B both on day 1 | not allowed | - | 6 + 6 = 12 is more than 10 |

`StarterTests` runs this exact example as the "final boss" test.

## The four OOP requirements
| # | Requirement | How it is checked |
|---|-------------|-------------------|
| 1 | Add a new restriction without rewriting the planner | **Diego reviews this one in person** |
| 2 | Recalculate the optimal schedule | Automatic. One planner object is reused for many scenarios, so keep no leftovers between calls |
| 3 | Preserve the original warehouse state | Automatic. Never change the warehouse or any input |
| 4 | Return a new valid plan whenever a safety rule, a truck or anything else changes | Automatic. Your plan is checked again after a truck or a rule changes |

Requirements 2 to 4 are checked automatically on your pull request.

Hint for 1: make one `Rule` interface and many small rule classes (capacity, hazmat, truck availability). The planner asks every rule "is this OK?". A new restriction is then a new class, not a rewrite. Never heard of interfaces? Read about them in the official Java docs.

## How the summary shows it
| Summary line | Meaning |
|--------------|---------|
| `⭐ Bonus Part 4 · Operation Recovery: Passed` | Every Part 4 case is right, including requirements 2 to 4 |
| `Bonus Part 4 · Operation Recovery: not passed yet` | Keep going, or skip it. Nothing else is affected |

Part 4 never changes the color of the check.

## Good luck
The storm is not going to wait. Plan well.
