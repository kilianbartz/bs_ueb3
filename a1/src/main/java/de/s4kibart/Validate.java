package de.s4kibart;

import me.tongfei.progressbar.ProgressBar;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

public class Validate {

    private static final Random random = new Random();
    private static final int TOTAL_ITERATIONS = 1000;
    //    private static final String[] finalNames = {"file1", "file2", "file3", "file4", "file5", "file6", "file7", "file8", "file9", "file10"};
    private static final String[] finalNames = {"file1"};
    private static final Config cfg = new Config("tank", "/v1");

    private static String getRandomString(String s) {
        int start = random.nextInt(0, s.length() - 1);
        return s.substring(start, random.nextInt(start + 1, s.length()));
    }

    private static int workload_write_single(String sampleText, int iterations) {
        int collisions = 0;
        for (int i = 0; i < iterations; i++) {
            String fileName = finalNames[random.nextInt(0, finalNames.length)];
            Transaction t = new Transaction(cfg, UUID.randomUUID().toString());
            t.write(fileName, getRandomString(sampleText));
            if (!t.commit())
                collisions++;
        }
        return collisions;
    }

    private static int workload_write_read_single(String sampleText, int iterations) {
        int collisions = 0;
        for (int i = 0; i < iterations; i++) {
            String fileName = finalNames[random.nextInt(0, finalNames.length)];
            if (random.nextFloat() < 0.5) {
                // write
                Transaction t = new Transaction(cfg, UUID.randomUUID().toString());
                t.write(fileName, getRandomString(sampleText));
                if (!t.commit())
                    collisions++;
            } else {
                // read
                Transaction t = new Transaction(cfg, UUID.randomUUID().toString());
                if (!t.read(fileName))
                    collisions++;
                t.commit();
            }

        }
        return collisions;
    }

    private static int executeWithMultipleProcesses(Callable<Integer> c, int numProcesses) {
        try {
            final ExecutorService service = Executors.newFixedThreadPool(numProcesses);
            long start = System.currentTimeMillis();
            List<Callable<Integer>> callables = new ArrayList<>();
            for (int i = 0; i < numProcesses; i++) {
                callables.add(c);
            }
            List<Future<Integer>> futures = service.invokeAll(callables);
            int collisions = futures.stream().mapToInt(e -> {
                try {
                    return e.get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
            }).sum();
            double duration = (System.currentTimeMillis() - start) / 1000.;
            System.out.println(numProcesses + " processes took " + duration + "s (" + collisions + " collisions).");
            service.shutdown();
            return collisions;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        InputStream is = Validate.class.getResourceAsStream("/sample_text.txt");
        BufferedReader bis = new BufferedReader(new InputStreamReader(is));
        String line;
        StringBuilder sb = new StringBuilder();
        while ((line = bis.readLine()) != null) {
            sb.append(line);
        }
        String content = sb.toString();

        // only writes
        for (int i = 1; i <= 4; i++) {
            int iterationsTwoProcesses = TOTAL_ITERATIONS / i;
            Callable<Integer> r = () -> workload_write_single(content, iterationsTwoProcesses);
            int collisions = executeWithMultipleProcesses(r, i);
            System.out.println("(" + collisions + " collisions)");
        }
        // reads_and_writes
        for (int i = 1; i <= 4; i++) {
            int iterationsTwoProcesses = TOTAL_ITERATIONS / i;
            Callable<Integer> r = () -> workload_write_read_single(content, iterationsTwoProcesses);
            int collisions = executeWithMultipleProcesses(r, i);
            System.out.println("(" + collisions + " collisions)");
        }
    }
}
