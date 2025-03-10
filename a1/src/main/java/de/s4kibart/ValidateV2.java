package de.s4kibart;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ValidateV2 {

    private static final Random random = new Random();
    //    private static final String[] finalNames = {"file1", "file2", "file3", "file4", "file5", "file6", "file7", "file8", "file9", "file10"};
    private static final String[] finalNames = {"file1"};
    //    private static final String[] finalNames = {"file1", "file2", "file3", "file4", "file5"};
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
            TransactionV2 t = new TransactionV2(cfg, UUID.randomUUID().toString());
            t.write(fileName, getRandomString(sampleText));
            if (!t.commit()) {
                collisions++;
                totalResetTime += 0;
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
                TransactionV2 t = new TransactionV2(cfg, UUID.randomUUID().toString());
                t.write(fileName, getRandomString(sampleText));
                if (!t.commit()) {
                    collisions++;
                    totalResetTime += 0;
                }
            } else {
                // read
                TransactionV2 t = new TransactionV2(cfg, UUID.randomUUID().toString());
                if (t.read(fileName) == null) {
                    collisions++;
                    totalResetTime += 0;
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
        if (args.length != 1) {
            System.err.println("You have to provide the total number of iterations for each test as argument 1.");
            System.exit(2);
        }
        int TOTAL_ITERATIONS = Integer.parseInt(args[0]);

        InputStream is = Validate.class.getResourceAsStream("/sample_text.txt");
        BufferedReader bis = new BufferedReader(new InputStreamReader(is));
        String line;
        StringBuilder sb = new StringBuilder();
        while ((line = bis.readLine()) != null) {
            sb.append(line);
        }
        String content = sb.toString();

        System.out.println("validating using " + TOTAL_ITERATIONS + " iterations and " + finalNames.length + " files...");
        // only writes
        System.out.println("only write times: --------------------------------");
        for (int i = 1; i <= 4; i++) {
            int iterationsTwoProcesses = TOTAL_ITERATIONS / i;
            Callable<ValidationPair> r = () -> workload_write_single(content, iterationsTwoProcesses);
            executeWithMultipleProcesses(r, i);
        }
        System.out.println("-------------------------------------");
        System.out.println("read + write times: --------------------------------");
        // reads_and_writes
        for (int i = 1; i <= 4; i++) {
            int iterationsTwoProcesses = TOTAL_ITERATIONS / i;
            Callable<ValidationPair> r = () -> workload_write_read_single(content, iterationsTwoProcesses);
            executeWithMultipleProcesses(r, i);
        }
        System.out.println("-------------------------------------");
    }
}
