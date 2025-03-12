package de.s4kibart;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import org.json.JSONObject;

public class Validate {

    private static final Random random = new Random();
    private static final String[] finalNames10 = { "file1", "file2", "file3", "file4", "file5", "file6", "file7",
            "file8",
            "file9", "file10" };
    private static final String[] finalNames1 = { "file1" };
    private static final String[] finalNames5 = { "file1", "file2", "file3",
            "file4", "file5" };
    private static final Config cfg = new Config("tank", "/v1");

    private static String getRandomString(String s) {
        int start = random.nextInt(0, s.length() - 1);
        return s.substring(start, random.nextInt(start + 1, s.length()));
    }

    private static ValidationPair workload_write_single(String[] finalNames, String sampleText, int iterations) {
        int collisions = 0;
        long totalPersistTime = 0;
        for (int i = 0; i < iterations; i++) {
            String fileName = finalNames[random.nextInt(0, finalNames.length)];
            Transaction t = new Transaction(cfg, UUID.randomUUID().toString());
            t.write(fileName, getRandomString(sampleText));
            long resetTime = t.commit();
            if (resetTime > 0) {
                collisions++;
            } else {
                totalPersistTime += resetTime;
            }
        }
        return new ValidationPair(collisions, (-1) * totalPersistTime);
    }

    private static ValidationPair workload_write_read_single(String[] finalNames, String sampleText, int iterations) {
        int collisions = 0;
        long totalPersistTime = 0;
        for (int i = 0; i < iterations; i++) {
            String fileName = finalNames[random.nextInt(0, finalNames.length)];

            Transaction t = new Transaction(cfg, UUID.randomUUID().toString());
            if (random.nextFloat() < 0.5)
                t.write(fileName, getRandomString(sampleText));
            else
                t.read(fileName);
            long resetTime = t.commit();
            if (resetTime > 0) {
                collisions++;
            } else {
                totalPersistTime += resetTime;
            }

        }
        return new ValidationPair(collisions, (-1) * totalPersistTime);

    }

    private static ValidationPair workload_write_single_NoBuffering(String[] finalNames, String sampleText,
            int iterations) {
        int collisions = 0;
        long totalResetTime = 0;
        for (int i = 0; i < iterations; i++) {
            String fileName = finalNames[random.nextInt(0, finalNames.length)];
            TransactionNoBuffering t = new TransactionNoBuffering(cfg, UUID.randomUUID().toString());
            t.write(fileName, getRandomString(sampleText));
            long resetTime = t.commit();
            if (resetTime > 0) {
                collisions++;
                totalResetTime += resetTime;
            }
        }
        return new ValidationPair(collisions, totalResetTime);
    }

    private static ValidationPair workload_write_read_single_NoBuffering(String[] finalNames, String sampleText,
            int iterations) {
        int collisions = 0;
        long totalResetTime = 0;
        for (int i = 0; i < iterations; i++) {
            String fileName = finalNames[random.nextInt(0, finalNames.length)];

            Transaction t = new Transaction(cfg, UUID.randomUUID().toString());
            if (random.nextFloat() < 0.5)
                t.write(fileName, getRandomString(sampleText));
            else
                t.read(fileName);
            long resetTime = t.commit();
            if (resetTime > 0) {
                collisions++;
                totalResetTime += resetTime;
            }

        }
        return new ValidationPair(collisions, totalResetTime);

    }

    private static FourTuple executeWithMultipleProcesses(Callable<ValidationPair> c, int numProcesses) {
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
            System.out.println(numProcesses + " processes took " + duration + "s (" + collisions
                    + " collisions, average reset time: " + avgTime + "ms).");
            service.shutdown();
            return new FourTuple(numProcesses, collisions, avgTime, duration);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println(
                    "You have to provide the total number of iterations for each test as argument 1 and the number of files to use (1,5,10) as argument 2 and the method (Transaction, TransactionNoBuffering) as argument 3.");
            System.exit(2);
        }
        int TOTAL_ITERATIONS = Integer.parseInt(args[0]);
        int numFiles = Integer.parseInt(args[1]);
        String method = args[2];
        if (!method.equalsIgnoreCase("Transaction") && !method.equalsIgnoreCase("Transaction")) {
            System.err.println(
                    "You have to provide the total number of iterations for each test as argument 1 and the number of files to use (1,5,10) as argument 2 and the method (Transaction, TransactionNoBuffering) as argument 3.");
            System.exit(2);
        }

        String[] finalNames = finalNames1;
        if (numFiles == 5)
            finalNames = finalNames5;
        if (numFiles == 10)
            finalNames = finalNames10;

        final String[] fileNames = finalNames;
        JSONObject jo = new JSONObject();

        // prepare sample text
        InputStream is = Validate.class.getResourceAsStream("/sample_text.txt");
        BufferedReader bis = new BufferedReader(new InputStreamReader(is));
        String line;
        StringBuilder sb = new StringBuilder();
        while ((line = bis.readLine()) != null) {
            sb.append(line);
        }
        String content = sb.toString();

        System.out
                .println("validating using " + TOTAL_ITERATIONS + " iterations and " + finalNames.length + " files...");
        // only writes
        System.out.println("only write times: --------------------------------");
        for (int i = 1; i <= 4; i++) {
            int iterationsTwoProcesses = TOTAL_ITERATIONS / i;
            Callable<ValidationPair> r = () -> workload_write_single(fileNames, content, iterationsTwoProcesses);
            if (method.equalsIgnoreCase("TransactionNoBuffering"))
                r = () -> workload_write_single_NoBuffering(fileNames, content, iterationsTwoProcesses);
            FourTuple output = executeWithMultipleProcesses(r, i);
            jo.put("only_write_" + i, output.toJsonObject());
        }
        System.out.println("-------------------------------------");
        System.out.println("read + write times: --------------------------------");
        // reads_and_writes
        for (int i = 1; i <= 4; i++) {
            int iterationsTwoProcesses = TOTAL_ITERATIONS / i;
            Callable<ValidationPair> r = () -> workload_write_read_single(fileNames, content, iterationsTwoProcesses);
            if (method.equalsIgnoreCase("TransactionNoBuffering"))
                r = () -> workload_write_read_single_NoBuffering(fileNames, content, iterationsTwoProcesses);
            FourTuple output = executeWithMultipleProcesses(r, i);
            jo.put("read_write_" + i, output.toJsonObject());
        }
        System.out.println("-------------------------------------");
        FileWriter fw = new FileWriter("validation_" + method + "_" + TOTAL_ITERATIONS + "_" + numFiles + ".json");
        fw.write(jo.toString());
        fw.close();
    }
}

class ValidationPair {
    public int numCollisions;
    public double avgResetTime;

    public ValidationPair(int numCollisions, long totalResetTime) {
        this.numCollisions = numCollisions;
        avgResetTime = numCollisions == 0 ? 0 : totalResetTime / (double) numCollisions;
    }
}

class FourTuple {
    public int numProcesses;
    public int collisions;
    public double avgTime;
    public double duration;

    public FourTuple(int numProcesses, int collisions, double avgTime, double duration) {
        this.numProcesses = numProcesses;
        this.avgTime = avgTime;
        this.collisions = collisions;
        this.duration = duration;
    }

    public String toString() {
        return numProcesses + "," + duration + "," + collisions + "," + avgTime;
    }

    public JSONObject toJsonObject() {
        JSONObject temp = new JSONObject();
        temp.put("numProcesses", numProcesses);
        temp.put("duration", duration);
        temp.put("collisions", collisions);
        temp.put("avgTime", avgTime);
        return temp;
    }
}
