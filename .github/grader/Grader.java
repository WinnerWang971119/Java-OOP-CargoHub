import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import org.amped.model.Cargo;
import org.amped.model.Shipment;
import org.amped.model.Warehouse;
import org.amped.model.recovery.Assignment;
import org.amped.model.recovery.Plan;
import org.amped.model.recovery.RecoveryCargo;
import org.amped.model.recovery.Scenario;
import org.amped.model.recovery.Truck;
import org.amped.service.CargoManager;
import org.amped.service.RecoveryPlanner;

/**
 * Grades a student's CargoManager and RecoveryPlanner against the test data.
 *
 * Usage: java -cp BUILD Grader DATA_DIR RESULT_FILE
 *
 * What it does, in order:
 *   1. Reads the four data files strictly into memory.
 *   2. Deletes DATA_DIR (a working copy made by the caller).
 *   3. Grades every part on its own: batch (PART1), optimize (PART2), trucks (PART3),
 *      recovery (PART4). A part stops at its first wrong or slow case; the next part still runs.
 *   4. Writes RESULT_FILE, prints the same lines to the real stdout and halts.
 *
 * The result file has one line per part, in order:
 *   PART1 PASSED or PART1 FAILED, then PART2 ..., PART3 ... and PART4 ...
 * Which parts are required is decided by the caller (run.sh), not here.
 *
 * The exit status is always 0. The caller reads the result file. A missing file
 * means the student code killed the JVM or the data was bad, which counts as failed.
 *
 * The output never names a case, a SKU or any data.
 */
public class Grader {
    private static final long BATCH_LIMIT_MS = 2_000;
    private static final long OPTIMIZE_LIMIT_MS = 2_000;
    private static final long TRUCKS_LIMIT_MS = 3_000;
    private static final long RECOVERY_LIMIT_MS = 5_000;
    /**
     * Stop grading after this long, so the result file always gets written before the caller's
     * timeout. Each part gets a share in proportion to (number of cases * its per-call limit), and
     * has to be done by the end of its share plus the shares before it, so a slow part cannot starve
     * the next ones. The last case to start can still use up its full per-call limit (5 s at most),
     * so this plus 5 s must stay well under the 300 s that run.sh allows.
     */
    private static final long TOTAL_BUDGET_MS = 240_000;

    private final PrintStream realOut = System.out;
    private final PrintStream realErr = System.err;
    private final PrintStream discard = new PrintStream(OutputStream.nullOutputStream());
    /** Set once a student call timed out: its thread may still print, so System.out stays swapped. */
    private boolean streamsLocked;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("usage: java Grader <dataDir> <resultFile>");
            System.exit(2);
        }
        new Grader().run(Paths.get(args[0]), Paths.get(args[1]));
    }

    // ---------------------------------------------------------------------------------------------
    // Top level
    // ---------------------------------------------------------------------------------------------

    /** One graded part. The cases are already in memory. */
    private interface Part {
        /**
         * Runs every case and stops at the first wrong or slow one, or when the deadline
         * (a System.nanoTime() value) has passed. True only if all cases pass.
         */
        boolean passes(long deadlineNanos);
    }

    /**
     * A part, the label that starts its line in the result file, and its weight: the most time its
     * cases can take in total (number of cases * per-call limit). The weights split the time budget.
     */
    private record GradedPart(String label, long weightMs, Part part) {}

    private void run(Path dataDir, Path resultFile) {
        List<String> verdict = null;
        try {
            verdict = grade(dataDir, resultFile);
        } catch (DataException e) {
            realErr.println("grader: bad data: " + e.getMessage());
        } catch (Throwable t) {
            realErr.println("grader: internal error: " + t.getClass().getSimpleName());
        }
        // No verdict (bad data, internal error): no result file is written, and the caller counts that as failed.
        finish(resultFile, verdict);
    }

    private List<String> grade(Path dataDir, Path resultFile) throws IOException, DataException {
        Files.deleteIfExists(resultFile);
        if (!Files.isDirectory(dataDir, LinkOption.NOFOLLOW_LINKS)) {
            throw new DataException("the data directory is missing");
        }

        // Every part, in the order of the result file. To add one: write its loader and its check
        // method, then add one line here. All data is read before the first student call and the
        // files are deleted right after.
        List<GradedPart> parts = new ArrayList<>();
        parts.add(part("PART1", loadBatch(dataDir.resolve("batch.txt")), BATCH_LIMIT_MS, this::checkBatch));
        parts.add(part("PART2", loadOptimize(dataDir.resolve("optimize.txt")), OPTIMIZE_LIMIT_MS,
                this::checkOptimize));
        parts.add(part("PART3", loadTrucks(dataDir.resolve("trucks.txt")), TRUCKS_LIMIT_MS, this::checkTrucks));
        parts.add(part("PART4", loadRecovery(dataDir.resolve("recovery.txt")), RECOVERY_LIMIT_MS,
                this::checkRecovery));
        deleteTree(dataDir);

        long totalWeight = 0;
        for (GradedPart graded : parts) {
            totalWeight += graded.weightMs();
        }
        totalWeight = Math.max(1, totalWeight);

        long start = System.nanoTime();
        double budgetNanos = TOTAL_BUDGET_MS * 1_000_000.0;
        long weightSoFar = 0;
        List<String> verdict = new ArrayList<>();
        for (GradedPart graded : parts) {
            weightSoFar += graded.weightMs();
            long deadline = start + (long) (budgetNanos * weightSoFar / totalWeight);
            boolean passed;
            try {
                passed = graded.part().passes(deadline);
            } catch (Throwable t) {
                passed = false;
            }
            verdict.add(graded.label() + (passed ? " PASSED" : " FAILED"));
        }
        return verdict;
    }

    /** Writes the result file, prints the same lines to the real stdout, then ends the JVM. */
    private void finish(Path resultFile, List<String> verdict) {
        if (verdict != null) {
            StringBuilder text = new StringBuilder();
            for (String line : verdict) {
                text.append(line).append('\n');
            }
            try {
                Path parent = resultFile.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(resultFile, text, StandardCharsets.UTF_8);
            } catch (Throwable t) {
                realErr.println("grader: could not write the result file");
            }
            realOut.print(text);
        }
        realOut.flush();
        realErr.flush();
        // halt, not exit: a stuck student thread must not be able to delay or change anything now.
        Runtime.getRuntime().halt(0);
    }

    private <T> GradedPart part(String label, List<T> cases, long callLimitMs, Function<T, String> check) {
        return new GradedPart(label, cases.size() * callLimitMs, deadline -> runCases(cases, check, deadline));
    }

    private <T> boolean runCases(List<T> cases, Function<T, String> check, long deadlineNanos) {
        for (T oneCase : cases) {
            if (System.nanoTime() - deadlineNanos > 0) {
                return false;
            }
            String problem;
            try {
                problem = check.apply(oneCase);
            } catch (Throwable t) {
                problem = "the check itself failed on this answer";
            }
            if (problem != null) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Calling student code
    // ---------------------------------------------------------------------------------------------

    private static final class Outcome<R> {
        volatile R value;
        volatile boolean threw;
        volatile boolean timedOut;

        boolean completed() {
            return !threw && !timedOut;
        }
    }

    /**
     * Runs the task on a daemon thread and waits at most limitMs. Student prints are discarded while it
     * runs. On a timeout the thread is left behind and the streams stay swapped.
     */
    private <R> Outcome<R> callStudent(Callable<R> task, long limitMs) {
        Outcome<R> outcome = new Outcome<>();
        Thread worker = new Thread(() -> {
            try {
                outcome.value = task.call();
            } catch (Throwable t) {
                outcome.threw = true;
            }
        }, "student-code");
        worker.setDaemon(true);

        System.setOut(discard);
        System.setErr(discard);
        try {
            worker.start();
            worker.join(limitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (worker.isAlive()) {
                outcome.timedOut = true;
                streamsLocked = true;
            }
            if (!streamsLocked) {
                System.setOut(realOut);
                System.setErr(realErr);
            }
        }
        return outcome;
    }

    // ---------------------------------------------------------------------------------------------
    // Part 1: processBatch
    // ---------------------------------------------------------------------------------------------

    /** Returns null when the case passes, otherwise a short reason (never printed). */
    private String checkBatch(BatchCase c) {
        Map<String, Cargo> cargoBySku = new HashMap<>();
        // Expected final stock of every SKU in the case: initial, plus the net change if the batch is accepted.
        Map<String, Integer> expectedStock = new LinkedHashMap<>();
        Warehouse warehouse = new Warehouse(c.capacity());
        for (Stock s : c.stock()) {
            warehouse.addStock(cargoFor(cargoBySku, s.sku(), s.unitWeight()), s.qty());
            expectedStock.merge(s.sku(), s.qty(), Integer::sum);
        }
        List<Shipment> batch = new ArrayList<>();
        for (Line l : c.lines()) {
            batch.add(new Shipment(cargoFor(cargoBySku, l.sku(), l.unitWeight()), l.qty(), l.inbound()));
            expectedStock.putIfAbsent(l.sku(), 0);
            if (c.expect()) {
                expectedStock.merge(l.sku(), l.inbound() ? l.qty() : -l.qty(), Integer::sum);
            }
        }

        WarehouseSnapshot before = WarehouseSnapshot.of(warehouse, expectedStock.keySet());
        Outcome<Boolean> outcome = callStudent(() -> new CargoManager(warehouse).processBatch(batch), BATCH_LIMIT_MS);
        if (!outcome.completed()) {
            return "timeout or exception";
        }
        if (!Boolean.valueOf(c.expect()).equals(outcome.value)) {
            return "wrong return value";
        }
        for (Map.Entry<String, Integer> e : expectedStock.entrySet()) {
            if (warehouse.getStock(e.getKey()) != e.getValue()) {
                return "wrong final stock";
            }
        }
        // A rejected batch must leave no trace at all: not even a newly registered SKU.
        if (!c.expect() && !before.matches(warehouse)) {
            return "warehouse changed";
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Part 2: optimizeInbound
    // ---------------------------------------------------------------------------------------------

    private String checkOptimize(OptimizeCase c) {
        Map<String, Cargo> cargoBySku = new HashMap<>();
        Warehouse warehouse = new Warehouse(c.capacity());
        Set<String> skus = new HashSet<>();
        for (Stock s : c.stock()) {
            warehouse.addStock(cargoFor(cargoBySku, s.sku(), s.unitWeight()), s.qty());
            skus.add(s.sku());
        }
        List<Shipment> candidates = new ArrayList<>();
        IdentityHashMap<Object, Boolean> candidateSet = new IdentityHashMap<>();
        for (Candidate cand : c.candidates()) {
            Shipment shipment = new Shipment(cargoFor(cargoBySku, cand.sku(), cand.unitWeight()), cand.qty(), true,
                    cand.priority());
            candidates.add(shipment);
            candidateSet.put(shipment, Boolean.TRUE);
            skus.add(cand.sku());
        }
        int freeCapacity = warehouse.getFreeCapacity();
        WarehouseSnapshot before = WarehouseSnapshot.of(warehouse, skus);

        // The student gets its own copy of the list; the answer is copied inside the guarded thread.
        List<Shipment> given = new ArrayList<>(candidates);
        Outcome<List<?>> outcome = callStudent(() -> {
            List<Shipment> answer = new CargoManager(warehouse).optimizeInbound(given);
            return answer == null ? null : new ArrayList<Object>(answer);
        }, OPTIMIZE_LIMIT_MS);
        if (!outcome.completed()) {
            return "timeout or exception";
        }
        List<?> answer = outcome.value;
        if (answer == null) {
            return "returned null";
        }

        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        long totalWeight = 0;
        long totalPriority = 0;
        for (Object item : answer) {
            if (!candidateSet.containsKey(item)) {
                return "not one of the candidates";
            }
            if (seen.put(item, Boolean.TRUE) != null) {
                return "same candidate twice";
            }
            Shipment shipment = (Shipment) item;
            totalWeight += shipment.getWeight();
            totalPriority += shipment.getPriority();
        }
        if (totalWeight > freeCapacity) {
            return "over the free capacity";
        }
        if (totalPriority != c.best()) {
            return "not the best total priority";
        }
        if (!before.matches(warehouse)) {
            return "warehouse changed";
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Part 3 (bonus): planTrucks
    // ---------------------------------------------------------------------------------------------

    private String checkTrucks(TruckCase c) {
        Map<String, Cargo> cargoBySku = new HashMap<>();
        List<Shipment> ships = new ArrayList<>();
        IdentityHashMap<Object, Boolean> shipSet = new IdentityHashMap<>();
        for (Ship s : c.ships()) {
            Shipment shipment = new Shipment(cargoFor(cargoBySku, s.sku(), s.unitWeight()), s.qty(), false);
            ships.add(shipment);
            shipSet.put(shipment, Boolean.TRUE);
        }
        // The grader's own record of the conflicts; the student gets fresh copies.
        Set<List<String>> conflictPairs = new HashSet<>();
        List<String[]> givenConflicts = new ArrayList<>();
        for (String[] pair : c.conflicts()) {
            conflictPairs.add(pairOf(pair[0], pair[1]));
            givenConflicts.add(new String[] {pair[0], pair[1]});
        }

        List<Shipment> givenShips = new ArrayList<>(ships);
        Warehouse warehouse = new Warehouse(0);
        Set<String> shipSkus = new HashSet<>();
        for (Ship s : c.ships()) {
            shipSkus.add(s.sku());
        }
        WarehouseSnapshot before = WarehouseSnapshot.of(warehouse, shipSkus);
        Outcome<List<List<?>>> outcome = callStudent(() -> {
            List<List<Shipment>> answer = new CargoManager(warehouse)
                    .planTrucks(givenShips, c.truckCapacity(), givenConflicts);
            if (answer == null) {
                return null;
            }
            List<List<?>> copy = new ArrayList<>();
            for (List<Shipment> truck : answer) {
                copy.add(truck == null ? null : new ArrayList<Object>(truck));
            }
            return copy;
        }, TRUCKS_LIMIT_MS);
        if (!outcome.completed()) {
            return "timeout or exception";
        }
        if (!before.matches(warehouse)) {
            return "warehouse changed";
        }
        List<List<?>> trucks = outcome.value;

        if (c.best() == -1) {
            return trucks == null ? null : "should have returned null";
        }
        if (trucks == null) {
            return "returned null";
        }

        IdentityHashMap<Object, Boolean> placed = new IdentityHashMap<>();
        for (List<?> truck : trucks) {
            if (truck == null || truck.isEmpty()) {
                return "empty truck";
            }
            long load = 0;
            for (int i = 0; i < truck.size(); i++) {
                Object item = truck.get(i);
                if (!shipSet.containsKey(item)) {
                    return "not one of the shipments";
                }
                if (placed.put(item, Boolean.TRUE) != null) {
                    return "shipment on two trucks";
                }
                Shipment shipment = (Shipment) item;
                load += shipment.getWeight();
                for (int j = 0; j < i; j++) {
                    String a = shipment.getSku();
                    String b = ((Shipment) truck.get(j)).getSku();
                    if (!a.equals(b) && conflictPairs.contains(pairOf(a, b))) {
                        return "conflicting SKUs share a truck";
                    }
                }
            }
            if (load > c.truckCapacity()) {
                return "truck over capacity";
            }
        }
        if (placed.size() != ships.size()) {
            return "a shipment is on no truck";
        }
        if (trucks.size() != c.best()) {
            return "not the fewest trucks";
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Part 4 (bonus): Operation Recovery
    // ---------------------------------------------------------------------------------------------

    /** The one RecoveryPlanner for the whole part, created on the first guarded call (not on the grader thread). */
    private volatile RecoveryPlanner planner;

    /** The hazmat class of cargo that is not hazardous (the same word as in RecoveryCargo). */
    private static final String NOT_HAZMAT = "NONE";

    /** One assignment of the student's plan, copied once on the guarded thread. */
    private record Move(RecoveryCargo cargo, Truck truck, int day) {}

    private String checkRecovery(RecoveryCase c) {
        // Fresh objects for every case. The scenario gets its own copies of the lists; the grader keeps
        // the originals to compare after the call, and its own rows to judge the answer.
        Map<String, Cargo> cargoBySku = new HashMap<>();
        Warehouse warehouse = new Warehouse(c.capacity());
        for (Stock s : c.stock()) {
            warehouse.addStock(cargoFor(cargoBySku, s.sku(), s.unitWeight()), s.qty());
        }
        List<RecoveryCargo> cargoList = new ArrayList<>();
        IdentityHashMap<RecoveryCargo, Integer> cargoIndex = new IdentityHashMap<>();
        for (int i = 0; i < c.cargo().size(); i++) {
            CargoRow row = c.cargo().get(i);
            RecoveryCargo piece = new RecoveryCargo(row.id(), row.weight(), row.priority(), row.inbound(),
                    row.deadline(), row.hazmatClass());
            cargoList.add(piece);
            cargoIndex.put(piece, i);
        }
        List<Truck> truckList = new ArrayList<>();
        IdentityHashMap<Truck, Integer> truckIndex = new IdentityHashMap<>();
        for (int i = 0; i < c.trucks().size(); i++) {
            TruckRow row = c.trucks().get(i);
            Truck truck = new Truck(row.id(), row.capacity(), row.certified(), new ArrayList<>(row.days()));
            truckList.add(truck);
            truckIndex.put(truck, i);
        }
        List<String[]> conflictList = new ArrayList<>();
        Set<List<String>> conflictPairs = new HashSet<>();
        for (String[] pair : c.conflicts()) {
            conflictList.add(new String[] {pair[0], pair[1]});
            conflictPairs.add(pairOf(pair[0], pair[1]));
        }
        Scenario scenario = new Scenario(warehouse, c.horizon(), new ArrayList<>(cargoList),
                new ArrayList<>(truckList), new ArrayList<>(conflictList));

        // Taken before the call, so a planner that changes the warehouse cannot move the goalposts.
        WarehouseSnapshot before = WarehouseSnapshot.of(warehouse, List.of());
        long baseWeight = warehouse.getCurrentWeight();

        Outcome<List<Move>> outcome = callStudent(() -> {
            if (planner == null) {
                planner = new RecoveryPlanner();
            }
            Plan plan = planner.plan(scenario);
            if (plan == null) {
                return null;
            }
            List<Move> moves = new ArrayList<>();
            for (Assignment assignment : plan.getAssignments()) {
                moves.add(assignment == null
                        ? new Move(null, null, 0)
                        : new Move(assignment.getCargo(), assignment.getTruck(), assignment.getDay()));
            }
            return moves;
        }, RECOVERY_LIMIT_MS);
        if (!outcome.completed()) {
            return "timeout or exception";
        }

        // Nothing the student was given may have changed, whatever the answer is.
        if (!before.matches(warehouse)) {
            return "warehouse changed";
        }
        if (!sameObjects(scenario.getCargo(), cargoList) || !sameObjects(scenario.getTrucks(), truckList)
                || !sameObjects(scenario.getHazmatConflicts(), conflictList)) {
            return "scenario changed";
        }
        for (int i = 0; i < conflictList.size(); i++) {
            if (!Arrays.equals(conflictList.get(i), c.conflicts().get(i))) {
                return "scenario changed";
            }
        }

        List<Move> moves = outcome.value;
        if (!c.feasible()) {
            return moves == null ? null : "should have returned null";
        }
        if (moves == null) {
            return "returned null";
        }
        return judgePlan(c, moves, cargoIndex, truckIndex, conflictPairs, baseWeight);
    }

    /** Checks every rule of a plan and its score and trip count, using only the grader's own rows. */
    private static String judgePlan(RecoveryCase c, List<Move> moves, IdentityHashMap<RecoveryCargo, Integer> cargoIndex,
            IdentityHashMap<Truck, Integer> truckIndex, Set<List<String>> conflictPairs, long baseWeight) {
        int cargoCount = c.cargo().size();
        int daySlots = c.horizon() + 1;
        int[] shippedOn = new int[cargoCount]; // 0 = never shipped; real days start at 1
        // A trip is one truck on one day. Key = truck index * daySlots + day; value = the cargo on it.
        Map<Integer, List<Integer>> trips = new LinkedHashMap<>();
        long score = 0;

        for (Move move : moves) {
            Integer cargoAt = move.cargo() == null ? null : cargoIndex.get(move.cargo());
            Integer truckAt = move.truck() == null ? null : truckIndex.get(move.truck());
            if (cargoAt == null || truckAt == null) {
                return "not an object of this scenario";
            }
            if (shippedOn[cargoAt] != 0) {
                return "cargo shipped twice";
            }
            CargoRow piece = c.cargo().get(cargoAt);
            int day = move.day();
            if (day < piece.inbound() || day > c.horizon()) {
                return "day out of range";
            }
            if (!c.trucks().get(truckAt).days().contains(day)) {
                return "truck not available that day";
            }
            shippedOn[cargoAt] = day;
            trips.computeIfAbsent(truckAt * daySlots + day, key -> new ArrayList<>()).add(cargoAt);
            if (day <= piece.deadline()) {
                score += piece.priority();
            }
        }

        for (Map.Entry<Integer, List<Integer>> trip : trips.entrySet()) {
            TruckRow truck = c.trucks().get(trip.getKey() / daySlots);
            List<Integer> onTrip = trip.getValue();
            long load = 0;
            for (int i = 0; i < onTrip.size(); i++) {
                CargoRow piece = c.cargo().get(onTrip.get(i));
                load += piece.weight();
                if (!piece.hazmatClass().equals(NOT_HAZMAT) && !truck.certified()) {
                    return "hazmat on a truck that is not certified";
                }
                for (int j = 0; j < i; j++) {
                    String a = piece.hazmatClass();
                    String b = c.cargo().get(onTrip.get(j)).hazmatClass();
                    if (!a.equals(b) && conflictPairs.contains(pairOf(a, b))) {
                        return "conflicting hazmat classes share a trip";
                    }
                }
            }
            if (load > truck.capacity()) {
                return "truck over capacity";
            }
        }

        // Every morning, after the arrivals and before any truck leaves.
        for (int day = 1; day <= c.horizon(); day++) {
            long inWarehouse = baseWeight;
            for (int i = 0; i < cargoCount; i++) {
                CargoRow piece = c.cargo().get(i);
                boolean arrived = piece.inbound() <= day;
                boolean stillThere = shippedOn[i] == 0 || shippedOn[i] >= day;
                if (arrived && stillThere) {
                    inWarehouse += piece.weight();
                }
            }
            if (inWarehouse > c.capacity()) {
                return "warehouse over capacity in the morning";
            }
        }

        if (score != c.bestScore()) {
            return "not the best score";
        }
        if (trips.size() != c.bestTrips()) {
            return "not the fewest trips";
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Small helpers
    // ---------------------------------------------------------------------------------------------

    /** One Cargo object per SKU per case, shared by the warehouse and every shipment of that SKU. */
    private static Cargo cargoFor(Map<String, Cargo> cargoBySku, String sku, int unitWeight) {
        return cargoBySku.computeIfAbsent(sku, key -> new Cargo(key, unitWeight));
    }

    /** An unordered pair of SKUs as a map key. */
    private static List<String> pairOf(String a, String b) {
        return a.compareTo(b) <= 0 ? List.of(a, b) : List.of(b, a);
    }

    /** True if both lists hold the very same objects in the same order. */
    private static boolean sameObjects(List<?> now, List<?> before) {
        if (now.size() != before.size()) {
            return false;
        }
        for (int i = 0; i < now.size(); i++) {
            if (now.get(i) != before.get(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * What a warehouse looked like before a student call: its current weight, its registered SKUs in
     * order, and the stock of those SKUs plus any extra SKUs the case talks about. A student who
     * registers a new SKU, or adds or removes any stock, no longer matches.
     */
    private static final class WarehouseSnapshot {
        private final int currentWeight;
        private final List<String> skus;
        private final Map<String, Integer> stock = new LinkedHashMap<>();

        private WarehouseSnapshot(Warehouse warehouse, Collection<String> extraSkus) {
            this.currentWeight = warehouse.getCurrentWeight();
            this.skus = new ArrayList<>(warehouse.getSkus());
            for (String sku : skus) {
                stock.put(sku, warehouse.getStock(sku));
            }
            for (String sku : extraSkus) {
                stock.putIfAbsent(sku, warehouse.getStock(sku));
            }
        }

        static WarehouseSnapshot of(Warehouse warehouse, Collection<String> extraSkus) {
            return new WarehouseSnapshot(warehouse, extraSkus);
        }

        boolean matches(Warehouse warehouse) {
            if (warehouse.getCurrentWeight() != currentWeight || !skus.equals(warehouse.getSkus())) {
                return false;
            }
            for (Map.Entry<String, Integer> entry : stock.entrySet()) {
                if (warehouse.getStock(entry.getKey()) != entry.getValue()) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Deletes the directory and everything in it. Links are removed, never followed. */
    private static void deleteTree(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Data model
    // ---------------------------------------------------------------------------------------------

    private record Stock(String sku, int unitWeight, int qty) {}

    private record Line(String sku, int unitWeight, int qty, boolean inbound) {}

    private record Candidate(String sku, int unitWeight, int qty, int priority) {}

    private record Ship(String sku, int unitWeight, int qty) {}

    private record BatchCase(int capacity, List<Stock> stock, List<Line> lines, boolean expect) {}

    private record OptimizeCase(int capacity, List<Stock> stock, List<Candidate> candidates, int best) {}

    private record TruckCase(int truckCapacity, List<Ship> ships, List<String[]> conflicts, int best) {}

    private record TruckRow(String id, int capacity, boolean certified, List<Integer> days) {}

    private record CargoRow(String id, int weight, int priority, int inbound, int deadline, String hazmatClass) {}

    /** feasible is false for "best -1" (the right answer is null); then bestScore and bestTrips are unused. */
    private record RecoveryCase(int horizon, int capacity, List<Stock> stock, List<TruckRow> trucks,
            List<CargoRow> cargo, List<String[]> conflicts, boolean feasible, int bestScore, int bestTrips) {}

    // ---------------------------------------------------------------------------------------------
    // Strict data parsing. Error messages name the file and line, never the data on it.
    // ---------------------------------------------------------------------------------------------

    private static final class DataException extends Exception {
        private static final long serialVersionUID = 1L;

        DataException(String message) {
            super(message);
        }
    }

    /** The rows of one "case ... end" block, with the line number of each row. */
    private static final class Block {
        final String fileName;
        final int startLine;
        final List<String[]> rows = new ArrayList<>();
        final List<Integer> lineNumbers = new ArrayList<>();
        private final Map<String, Integer> unitWeights = new HashMap<>();

        Block(String fileName, int startLine) {
            this.fileName = fileName;
            this.startLine = startLine;
        }

        DataException error(int row, String message) {
            int line = row >= 0 ? lineNumbers.get(row) : startLine;
            return new DataException(fileName + " line " + line + ": " + message);
        }

        void need(boolean condition, int row, String message) throws DataException {
            if (!condition) {
                throw error(row, message);
            }
        }

        /** The number at this token of this row. */
        int number(int row, int token) throws DataException {
            try {
                return Integer.parseInt(rows.get(row)[token]);
            } catch (NumberFormatException e) {
                throw error(row, "not a whole number");
            }
        }

        int atLeast(int row, int token, int minimum) throws DataException {
            int value = number(row, token);
            need(value >= minimum, row, "number out of range");
            return value;
        }

        /** Reads the unit weight at token 2 of a row whose SKU is token 1; one SKU has one unit weight per case. */
        int unitWeight(int row) throws DataException {
            String sku = rows.get(row)[1];
            int weight = atLeast(row, 2, 1);
            Integer known = unitWeights.putIfAbsent(sku, weight);
            need(known == null || known == weight, row, "unit weight differs within the case");
            return weight;
        }
    }

    private static List<Block> readBlocks(Path file) throws IOException, DataException {
        String name = file.getFileName().toString();
        List<Block> blocks = new ArrayList<>();
        Block current = null;
        int lineNo = 0;
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] tokens = line.split("\\s+");
            if (tokens[0].equals("case")) {
                if (current != null || tokens.length != 2) {
                    throw new DataException(name + " line " + lineNo + ": misplaced 'case'");
                }
                current = new Block(name, lineNo);
            } else if (tokens[0].equals("end")) {
                if (current == null || tokens.length != 1) {
                    throw new DataException(name + " line " + lineNo + ": misplaced 'end'");
                }
                blocks.add(current);
                current = null;
            } else {
                if (current == null) {
                    throw new DataException(name + " line " + lineNo + ": row outside a case");
                }
                current.rows.add(tokens);
                current.lineNumbers.add(lineNo);
            }
        }
        if (current != null) {
            throw new DataException(name + ": the last case has no 'end'");
        }
        return blocks;
    }

    private static List<BatchCase> loadBatch(Path file) throws IOException, DataException {
        List<BatchCase> cases = new ArrayList<>();
        for (Block b : readBlocks(file)) {
            b.need(b.rows.size() >= 2, -1, "case is too short");
            int last = b.rows.size() - 1;
            String[] head = b.rows.get(0);
            b.need(head[0].equals("capacity") && head.length == 2, 0, "first row must be 'capacity'");
            int capacity = b.atLeast(0, 1, 0);
            String[] foot = b.rows.get(last);
            b.need(foot[0].equals("expect") && foot.length == 2
                    && (foot[1].equals("true") || foot[1].equals("false")), last, "last row must be 'expect'");
            boolean expect = foot[1].equals("true");

            List<Stock> stock = new ArrayList<>();
            List<Line> lines = new ArrayList<>();
            for (int r = 1; r < last; r++) {
                String[] t = b.rows.get(r);
                switch (t[0]) {
                    case "stock" -> {
                        b.need(t.length == 4 && lines.isEmpty(), r, "bad 'stock' row");
                        stock.add(new Stock(t[1], b.unitWeight(r), b.atLeast(r, 3, 0)));
                    }
                    case "in", "out" -> {
                        b.need(t.length == 4, r, "bad 'in'/'out' row");
                        lines.add(new Line(t[1], b.unitWeight(r), b.atLeast(r, 3, 1), t[0].equals("in")));
                    }
                    default -> throw b.error(r, "unknown row");
                }
            }
            cases.add(new BatchCase(capacity, stock, lines, expect));
        }
        return cases;
    }

    private static List<OptimizeCase> loadOptimize(Path file) throws IOException, DataException {
        List<OptimizeCase> cases = new ArrayList<>();
        for (Block b : readBlocks(file)) {
            b.need(b.rows.size() >= 2, -1, "case is too short");
            int last = b.rows.size() - 1;
            String[] head = b.rows.get(0);
            b.need(head[0].equals("capacity") && head.length == 2, 0, "first row must be 'capacity'");
            int capacity = b.atLeast(0, 1, 0);
            String[] foot = b.rows.get(last);
            b.need(foot[0].equals("best") && foot.length == 2, last, "last row must be 'best'");
            int best = b.atLeast(last, 1, 0);

            List<Stock> stock = new ArrayList<>();
            List<Candidate> candidates = new ArrayList<>();
            for (int r = 1; r < last; r++) {
                String[] t = b.rows.get(r);
                switch (t[0]) {
                    case "stock" -> {
                        b.need(t.length == 4 && candidates.isEmpty(), r, "bad 'stock' row");
                        stock.add(new Stock(t[1], b.unitWeight(r), b.atLeast(r, 3, 0)));
                    }
                    case "cand" -> {
                        b.need(t.length == 5, r, "bad 'cand' row");
                        candidates.add(new Candidate(t[1], b.unitWeight(r), b.atLeast(r, 3, 1), b.atLeast(r, 4, 1)));
                    }
                    default -> throw b.error(r, "unknown row");
                }
            }
            cases.add(new OptimizeCase(capacity, stock, candidates, best));
        }
        return cases;
    }

    private static List<TruckCase> loadTrucks(Path file) throws IOException, DataException {
        List<TruckCase> cases = new ArrayList<>();
        for (Block b : readBlocks(file)) {
            b.need(b.rows.size() >= 2, -1, "case is too short");
            int last = b.rows.size() - 1;
            String[] head = b.rows.get(0);
            b.need(head[0].equals("truck") && head.length == 2, 0, "first row must be 'truck'");
            int truckCapacity = b.atLeast(0, 1, 0);
            String[] foot = b.rows.get(last);
            b.need(foot[0].equals("best") && foot.length == 2, last, "last row must be 'best'");
            int best = b.atLeast(last, 1, -1);

            List<Ship> ships = new ArrayList<>();
            List<String[]> conflicts = new ArrayList<>();
            for (int r = 1; r < last; r++) {
                String[] t = b.rows.get(r);
                switch (t[0]) {
                    case "ship" -> {
                        b.need(t.length == 4 && conflicts.isEmpty(), r, "bad 'ship' row");
                        ships.add(new Ship(t[1], b.unitWeight(r), b.atLeast(r, 3, 1)));
                    }
                    case "conflict" -> {
                        b.need(t.length == 3 && !t[1].equals(t[2]), r, "bad 'conflict' row");
                        conflicts.add(new String[] {t[1], t[2]});
                    }
                    default -> throw b.error(r, "unknown row");
                }
            }
            cases.add(new TruckCase(truckCapacity, ships, conflicts, best));
        }
        return cases;
    }

    /** Limits of Part 4, as the README promises them; the data must keep to them. */
    private static final int RECOVERY_MAX_CARGO = 12;
    private static final int RECOVERY_MAX_TRUCKS = 4;
    private static final int RECOVERY_MAX_HORIZON = 3;

    private static List<RecoveryCase> loadRecovery(Path file) throws IOException, DataException {
        List<RecoveryCase> cases = new ArrayList<>();
        for (Block b : readBlocks(file)) {
            b.need(b.rows.size() >= 3, -1, "case is too short");
            int last = b.rows.size() - 1;
            String[] horizonRow = b.rows.get(0);
            b.need(horizonRow[0].equals("horizon") && horizonRow.length == 2, 0, "first row must be 'horizon'");
            int horizon = b.atLeast(0, 1, 1);
            b.need(horizon <= RECOVERY_MAX_HORIZON, 0, "number out of range");
            String[] capacityRow = b.rows.get(1);
            b.need(capacityRow[0].equals("capacity") && capacityRow.length == 2, 1, "second row must be 'capacity'");
            int capacity = b.atLeast(1, 1, 0);

            String[] foot = b.rows.get(last);
            b.need(foot[0].equals("best") && (foot.length == 2 || foot.length == 3), last,
                    "last row must be 'best'");
            boolean feasible = foot.length == 3;
            int bestScore = 0;
            int bestTrips = 0;
            if (feasible) {
                bestScore = b.atLeast(last, 1, 0);
                bestTrips = b.atLeast(last, 2, 0);
            } else {
                b.need(b.number(last, 1) == -1, last, "'best' needs -1, or a score and a trip count");
            }

            List<Stock> stock = new ArrayList<>();
            List<TruckRow> trucks = new ArrayList<>();
            List<CargoRow> cargo = new ArrayList<>();
            List<String[]> conflicts = new ArrayList<>();
            Set<String> truckIds = new HashSet<>();
            Set<String> cargoIds = new HashSet<>();
            int section = 0; // stock 1, truck 2, cargo 3, conflict 4: the rows come in this order
            for (int r = 2; r < last; r++) {
                String[] t = b.rows.get(r);
                int kind;
                switch (t[0]) {
                    case "stock" -> {
                        kind = 1;
                        b.need(t.length == 4, r, "bad 'stock' row");
                        stock.add(new Stock(t[1], b.unitWeight(r), b.atLeast(r, 3, 0)));
                    }
                    case "truck" -> {
                        kind = 2;
                        b.need(t.length == 5 && truckIds.add(t[1]), r, "bad 'truck' row");
                        b.need(t[3].equals("0") || t[3].equals("1"), r, "certified must be 0 or 1");
                        trucks.add(new TruckRow(t[1], b.atLeast(r, 2, 1), t[3].equals("1"),
                                parseDays(b, r, t[4], horizon)));
                    }
                    case "cargo" -> {
                        kind = 3;
                        b.need(t.length == 7 && cargoIds.add(t[1]), r, "bad 'cargo' row");
                        int inbound = b.number(r, 4);
                        b.need(inbound >= 1 && inbound <= horizon, r, "number out of range");
                        int deadline = b.number(r, 5);
                        b.need(deadline >= inbound && deadline <= horizon, r, "number out of range");
                        cargo.add(new CargoRow(t[1], b.atLeast(r, 2, 1), b.atLeast(r, 3, 1), inbound, deadline, t[6]));
                    }
                    case "conflict" -> {
                        kind = 4;
                        b.need(t.length == 3 && !t[1].equals(t[2]) && !t[1].equals(NOT_HAZMAT)
                                && !t[2].equals(NOT_HAZMAT), r, "bad 'conflict' row");
                        conflicts.add(new String[] {t[1], t[2]});
                    }
                    default -> throw b.error(r, "unknown row");
                }
                b.need(kind >= section, r, "rows out of order");
                section = kind;
            }
            b.need(trucks.size() <= RECOVERY_MAX_TRUCKS && cargo.size() <= RECOVERY_MAX_CARGO, -1,
                    "too many trucks or cargo");
            cases.add(new RecoveryCase(horizon, capacity, stock, trucks, cargo, conflicts, feasible, bestScore,
                    bestTrips));
        }
        return cases;
    }

    /** The days of a truck: "-" is none, otherwise a comma list of different days from 1 to the horizon. */
    private static List<Integer> parseDays(Block b, int row, String token, int horizon) throws DataException {
        List<Integer> days = new ArrayList<>();
        if (token.equals("-")) {
            return days;
        }
        for (String part : token.split(",", -1)) {
            int day;
            try {
                day = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                throw b.error(row, "not a whole number");
            }
            b.need(day >= 1 && day <= horizon && !days.contains(day), row, "bad day");
            days.add(day);
        }
        return days;
    }
}
