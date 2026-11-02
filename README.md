# CargoHub

You are the brain of a cargo warehouse. Decide what comes in, what goes out, and what fits.

## Start here
1. Open this folder in VS Code.
2. Open `src/org/amped/app/StarterTests.java` and press ▶ (Run).
3. Open `src/org/amped/service/CargoManager.java` and turn the FAILs into PASS. Its comments repeat every rule.

## Your job
| Part | Method | File (in `src/org/amped/`) | Required? |
|------|--------|----------------------------|-----------|
| 1 | `processBatch` | `service/CargoManager.java` | Required |
| 2 | `optimizeInbound` | `service/CargoManager.java` | Required |
| 3 | `planTrucks` | `service/CargoManager.java` | Bonus |
| 4 | `plan` | `service/RecoveryPlanner.java` | Bonus (final boss) |

## The API you get
Use these classes, do not work around them. `CargoManager` is yours: it keeps the warehouse (`new CargoManager(warehouse)`, `getWarehouse()`) and you write its three methods.

| Class | Method | Meaning |
|-------|--------|---------|
| Cargo | `new Cargo(sku, unitWeight)` | A name tag and the weight of ONE unit |
| | `getSku()` | The name tag, like `"A"` |
| | `getUnitWeight()` | Weight of one unit |
| Shipment | `new Shipment(cargo, quantity, inbound, priority)` | One line of a batch (`priority` is 0 if left out) |
| | `getCargo()` | The cargo being moved |
| | `getSku()` | Shortcut for `getCargo().getSku()` |
| | `getQuantity()` | How many units (1 or more) |
| | `isInbound()` | `true` = comes in, `false` = goes out |
| | `getPriority()` | Bigger = better (Part 2) |
| | `getWeight()` | `quantity * unitWeight` |
| Warehouse | `new Warehouse(capacity)` | An empty warehouse |
| | `getCapacity()` | The most weight it can hold |
| | `hasCargo(sku)` | `true` if this SKU was ever added |
| | `getCargo(sku)` | The `Cargo`, or `null` if unknown |
| | `getStock(sku)` | Units in stock (0 if unknown) |
| | `getCurrentWeight()` | Total weight in stock |
| | `getFreeCapacity()` | `capacity - currentWeight` |
| | `addStock(cargo, quantity)` | Add units. It does NOT check the capacity |
| | `removeStock(sku, quantity)` | Remove units. `false` and no change if too few |
| | `getSkus()` | A copy of the list of SKUs |

## Rules for Part 1 and Part 2
**Part 1: `processBatch(List<Shipment>)`** applies a batch, all or nothing.
`processBatch` returns `true` when the whole batch is applied and `false` when it is rejected.
- For each SKU: net = inbound minus outbound. Line order does not matter.
- Valid only if BOTH hold: stock + net is not below 0 for every SKU (a SKU never seen has stock 0), and the weight after is not above the capacity (exactly equal is fine).
- Valid: apply everything, return `true`. Not valid: change nothing, return `false`. An empty batch is valid.

**Part 2: `optimizeInbound(List<Shipment>)`** picks the best inbound shipments that fit.
- Budget = `getFreeCapacity()`. Total weight stays within it. Total priority is as big as possible.
- Return the same `Shipment` objects (no copies), each once, in any order. Never change the warehouse.
- Nothing fits or no candidates: return an empty list.
- Warning: best-looking first can lose. Free 10: A (weight 6, priority 12), B (5, 10), C (5, 10). A first gives 12. B + C gives 20.

## Part 3 (optional bonus): truck loading
`planTrucks(shipments, truckCapacity, conflicts)` returns a list of trucks. Each truck is a list of shipments.
- Every shipment goes on exactly one truck (the same objects). A truck carries at most `truckCapacity` weight.
- `conflicts` is a list of SKU pairs (hazmat). A pair never shares a truck. A SKU never conflicts with itself.
- Use the fewest trucks possible. No empty trucks.
- One shipment heavier than `truckCapacity`: return `null`. No shipments: return an empty list.

Example: limit 10, weights 4, 4, 3, 3, 3, 3.

| Plan | Trucks used |
|------|-------------|
| Biggest first | 4+4, 3+3+3, 3 = 3 trucks |
| Best | 4+3+3, 4+3+3 = 2 trucks |

## Part 4 (optional final boss): Operation Recovery
A storm hit. Trucks are down, shipments are late, and urgent cargo has deadlines.
Plan the next 1 to 3 days: what ships, on which truck, on which day.
Write `RecoveryPlanner.plan(Scenario)`. Read [OPERATION-RECOVERY.md](OPERATION-RECOVERY.md) first.

## Limits
| Part | Time per call | Size |
|------|---------------|------|
| 1 and 2 | 2 seconds | Part 2: up to 30 candidates, capacity up to 1000 (every subset is too slow) |
| 3 | 3 seconds | up to 15 shipments, `truckCapacity` up to 1000, up to 30 conflicts |
| 4 | 5 seconds | up to 12 cargo, 4 trucks, 3 days, capacity up to 1000 |

The whole check stops after about 4 minutes, so keep every call fast, not just under the limit.

## How to run
Press ▶ on `StarterTests.java` (or `Main.java`). Or, in a terminal in this folder:
```
javac -d bin -sourcepath src src/org/amped/app/StarterTests.java
java -cp bin org.amped.app.StarterTests
```

## How to submit
1. **Fork** this repo on GitHub, **clone** your fork, write your code and **commit often**.
2. Push, then open a **pull request** to `main`. The pull request is your submission.
3. The check **CargoHub grader** runs by itself on every push. Click **Details** to see the summary:
```
✅ Passed
Part 1 · processBatch: ✅ Passed
Part 2 · optimizeInbound: ✅ Passed
⭐ Bonus Part 3 · planTrucks: Passed
Bonus Part 4 · Operation Recovery: not passed yet
```
- Green means Parts 1 and 2 both pass. Bonus lines never change the color.
- It shows only pass or fail, so write your own tests. Push again to re-check, as often as you like.
- Only your files count: `CargoManager.java`, `RecoveryPlanner.java`, `StarterTests.java`, `Main.java` and your own new files in `service/` or `app/`. Changes to provided files are ignored.

## What to hand in
- `CargoManager.java` (plus `RecoveryPlanner.java` if you tried Part 4).
- A `## My solution` section at the END of this README: your approach and the time and space complexity.
- Your own tests, in `StarterTests.java` or new files.

## Grading
| Correctness & Edge Cases | Algorithm Design | OOP & API Integration | Code Quality & Testing |
|:---:|:---:|:---:|:---:|
| 35% | 30% | 20% | 15% |

Parts 3 and 4 are a bonus on top.

## No AI
No ChatGPT, Copilot, Claude, Gemini or any other AI tool. Not to write code, not to fix it, not for this README.
Turn off Copilot in VS Code before you start. You will walk Diego through your code, line by line, in person.
Stuck? Ask Diego, a mentor or a teammate.

## My solution
**Part 1 · processBatch.** Sum inbound and outbound per SKU, then check each SKU's stock + net >= 0 and the weight afterwards against the capacity. The warehouse is only touched after every check passes: inbound is added first, then outbound is removed. Time O(n + k), space O(k) for n lines and k SKUs.

**Part 2 · optimizeInbound.** 0/1 knapsack. `dp[i][w]` = best priority from the first i inbound candidates within weight w, with budget = free capacity. Walking the table backwards finds the chosen objects. Time and space O(n · C), at most 30 × 1000.

**Part 3 · planTrucks.** Exact subset DP. `ok[mask]` says whether a group fits on one truck with no conflict pair. `best[mask]` = fewest trucks, where each step picks a valid truck that contains the lowest remaining shipment, so each split is counted once. Time O(3^n) ≈ 14M steps for n = 15, space O(2^n).

**Part 4 · RecoveryPlanner.** Between days, all that matters is which cargo has shipped, so the state is a bitmask (at most 4096).
1. For each day, a subset DP over that day's trucks gives the fewest trips needed to ship any subset that evening. Each truck takes one allowed load.
2. A DP over the days moves from one "shipped" mask to the next. It checks the warehouse every morning and keeps the best (score, then fewest trips).
3. Back-pointers rebuild the `Plan`.

Time O(D · T · 3^n) ≈ 6.4M steps (about 90 ms at the limits), space O(D · T · 2^n).

**OOP.** Every trip restriction is a small `TripRule` class: `TruckAvailableRule`, `ArrivalRule`, `TruckCapacityRule`, `HazmatCertifiedRule` and `HazmatConflictRule`. A new restriction is a new class passed to `addRule` or the constructor, and the planner stays unchanged. The planner keeps no state between calls and only reads its inputs.

**Testing.** I added edge-case tests to `StarterTests`. I also checked every part against a brute-force solver on thousands of random small cases, with zero mismatches.
