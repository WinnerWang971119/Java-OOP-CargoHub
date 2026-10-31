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
import org.amped.service.CargoManager;

/**
 * Grades a student's CargoManager against the test data.
 *
 * Usage: java -cp BUILD Grader DATA_DIR RESULT_FILE
 *
 * What it does, in order:
 *   1. Reads the three data files strictly into memory.
 *   2. Deletes DATA_DIR (a working copy made by the caller).
 *   3. Grades every part on its own: batch (PART1), optimize (PART2), trucks (PART3).
 *      A part stops at its first wrong or slow case; the next part still runs.
 *   4. Writes RESULT_FILE, prints the same lines to the real stdout and halts.
 *
 * The result file has one line per part, in order:
 *   PART1 PASSED or PART1 FAILED, then PART2 ..., then PART3 ...
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
    /**
     * Stop grading after this long, so the result file always gets written before the caller's
     * timeout. Part k of n has to be done by k/n of it, so a slow part cannot starve the next ones.
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

    /** A part and the label that starts its line in the result file. */
    private record GradedPart(String label, Part part) {}

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
        parts.add(new GradedPart("PART1", part(loadBatch(dataDir.resolve("batch.txt")), this::checkBatch)));
        parts.add(new GradedPart("PART2", part(loadOptimize(dataDir.resolve("optimize.txt")), this::checkOptimize)));
        parts.add(new GradedPart("PART3", part(loadTrucks(dataDir.resolve("trucks.txt")), this::checkTrucks)));
        deleteTree(dataDir);

        long start = System.nanoTime();
        long budgetNanos = TOTAL_BUDGET_MS * 1_000_000L;
        List<String> verdict = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            GradedPart graded = parts.get(i);
            long deadline = start + budgetNanos * (i + 1) / parts.size();
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

    private <T> Part part(List<T> cases, Function<T, String> check) {
        return deadline -> runCases(cases, check, deadline);
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
        Map<String, Integer> stockBefore = new HashMap<>();
        for (String sku : skus) {
            stockBefore.put(sku, warehouse.getStock(sku));
        }

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
        for (Map.Entry<String, Integer> e : stockBefore.entrySet()) {
            if (warehouse.getStock(e.getKey()) != e.getValue()) {
                return "warehouse changed";
            }
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
}
