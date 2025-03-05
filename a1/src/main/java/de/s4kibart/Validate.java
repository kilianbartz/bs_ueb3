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

    private static ValidationPair workload_write_single(String sampleText, int iterations) {
        int collisions = 0;
        long totalResetTime = 0;
        for (int i = 0; i < iterations; i++) {
            String fileName = finalNames[random.nextInt(0, finalNames.length)];
            Transaction t = new Transaction(cfg, UUID.randomUUID().toString());
            t.write(fileName, getRandomString(sampleText));
            long resetTime = t.commit();
            if (resetTime > 0) {
                collisions++;
                totalResetTime += resetTime;
            }
        }
        return new ValidationPair(collisions, totalResetTime);
    }

    private static ValidationPair workload_write_read_single(String sampleText, int iterations) {
        int collisions = 0;
        long totalResetTime = 0;
        for (int i = 0; i < iterations; i++) {
            String fileName = finalNames[random.nextInt(0, finalNames.length)];
            if (random.nextFloat() < 0.5) {
                // write
                Transaction t = new Transaction(cfg, UUID.randomUUID().toString());
                t.write(fileName, getRandomString(sampleText));
                long resetTime = t.commit();
                if (resetTime > 0) {
                    collisions++;
                    totalResetTime += resetTime;
                }
            } else {
                // read
                Transaction t = new Transaction(cfg, UUID.randomUUID().toString());
                long resetTime = t.read(fileName);
                if (resetTime > 0) {
                    collisions++;
                    totalResetTime += resetTime;
                    t.commit();
                }

            }
        }
        return new ValidationPair(collisions, totalResetTime);
    }

    private static void executeWithMultipleProcesses(Callable<ValidationPair> c, int numProcesses) {
        try {
            final ExecutorService service = Executors.newFixedThreadPool(numProcesses);
            long start = System.currentTimeMillis();
            List<Callable<ValidationPair>> callables = new ArrayList<>();
            for (int i = 0; i < numProcesses; i++) {
                callables.add(c);
            }
            List<Future<ValidationPair>> futures = service.invokeAll(callables);
            int collisions = 0;
            double avgTime = 0;
            for (Future<ValidationPair> f : futures) {
                ValidationPair p = f.get();
                collisions += p.numCollisions;
                avgTime += p.avgResetTime;
            }
            double duration = (System.currentTimeMillis() - start) / 1000.;
            avgTime = avgTime / numProcesses;
            System.out.println(numProcesses + " processes took " + duration + "s (" + collisions + " collisions, average reset time: " + avgTime + "ms).");
            service.shutdown();
        } catch (InterruptedException | ExecutionException e) {
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
            Callable<ValidationPair> r = () -> workload_write_single(content, iterationsTwoProcesses);
            executeWithMultipleProcesses(r, i);
        }
        // reads_and_writes
        for (int i = 1; i <= 4; i++) {
            int iterationsTwoProcesses = TOTAL_ITERATIONS / i;
            Callable<ValidationPair> r = () -> workload_write_read_single(content, iterationsTwoProcesses);
            executeWithMultipleProcesses(r, i);
        }
    }
}


class ValidationPair {
    public int numCollisions;
    public double avgResetTime;

    public ValidationPair(int numCollisions, long totalResetTime) {
        this.numCollisions = numCollisions;
        avgResetTime = totalResetTime / (double) numCollisions;
    }
}
