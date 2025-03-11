package de.s4kibart;

import java.io.*;
import java.util.*;

public class TransactionNoBuffering implements Serializable {

    Config cfg;
    String name;
    HashMap<String, Long> fileTimestamps;
    boolean verbose = false;
    boolean startedCommit = false;
    List<String> relevantFiles;

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void store() {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(name + ".t");
            ObjectOutputStream out = new ObjectOutputStream(fileOutputStream);
            out.writeObject(this);
            out.close();
            fileOutputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hasConflicts() {
        HashMap<String, Long> currentFileTimestamps = computeFileTimestamps();
        // System.out.println("Transaction " + name + ": ------------------");
        for (String path : relevantFiles) {
            Long previousTimestamp = fileTimestamps.get(path);
            Long currentTimeStamp = currentFileTimestamps.get(path);
            if (previousTimestamp == null ^ currentTimeStamp == null) {
                if (verbose)
                    System.out.println("Transaction " + name + " has conflict with: " + path);
                return true;
            }
            if (!previousTimestamp.equals(currentTimeStamp)) {
                if (verbose)
                    System.out.println("Transaction " + name + " has conflict with: " + path);
                return true;
            }
        }
        return false;
    }

    // returns rollback time if fails; else -1
    public long commit() {
        startedCommit = true;
        store();
        if (hasConflicts())
            return rollbackSnapshot();

        if (verbose) {
            System.out.println("Transaction " + name + ": no conflict.");
            System.out.println("----------------------------------");
        }
        removeSnapshot();
        return -1;
    }

    private String headPath() {
        String fs = cfg.getZfsFilesystem();
        if (!cfg.getFileRoot().isEmpty())
            fs += cfg.getFileRoot();
        return "/" + fs;
    }

    public void write(String path, String content) {
        path = headPath() + "/" + path;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            writer.write(content);
            writer.flush();
            // update last modified time
            File file = new File(path);
            fileTimestamps.put(path, file.lastModified());
            relevantFiles.add(path);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void remove(String path) {
        path = headPath() + "/" + path;
        File file = new File(path);
        file.delete();
        // update last modified time
        fileTimestamps.remove(path);
        relevantFiles.add(path);
    }

    // returns rollback time if fails; else -1
    public long read(String path) {
        path = headPath() + "/" + path;
        relevantFiles.add(path);
        if (hasConflicts()) {
            return rollbackSnapshot();
        }
        // read the content of the file to output it
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append(System.lineSeparator());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (verbose)
            System.out.print(content);
        return -1;
    }

    private void executeCommand(String[] command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        try {
            Process process = processBuilder.start();
            int exitcode = process.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private String snapshotName() {
        String fs = cfg.getZfsFilesystem();
        return fs + "@" + name;
    }

    private void createSnapshot() {
        // zfs does not allow overwriting snapshots, so remove a snapshot of the same
        // name if it exists
        // removeSnapshot();
        String[] command = { "zfs", "snapshot", snapshotName() };
        executeCommand(command);
        // System.out.println("created snapshot " + snapshotName());
    }

    private long rollbackSnapshot() {
        if (verbose)
            System.out.println("resetting system to snapshot " + name + "...");
        long start = System.currentTimeMillis();
        String[] command = { "zfs", "rollback", snapshotName() };
        executeCommand(command);
        long rollbackTime = System.currentTimeMillis() - start;
        // because the transaction failed, start a new transaction from the new
        removeSnapshot();
        return rollbackTime;
    }

    private void removeSnapshot() {
        String[] command = { "zfs", "destroy", snapshotName() };
        executeCommand(command);
        // also remove the transaction file
        File file = new File(name + ".t");
        file.delete();
    }

    private HashMap<String, Long> computeFileTimestamps() {
        HashMap<String, Long> temp = new HashMap<>();
        File dir = new File(headPath());
        File[] files = dir.listFiles();

        // silly bugfix because this can be null in rare multithreaded events
        while (files == null)
            files = dir.listFiles();
        for (File file : Objects.requireNonNull(files)) {
            temp.put(file.getPath(), file.lastModified());
        }
        return temp;
    }

    public TransactionNoBuffering(Config cfg, String name) {
        this.cfg = cfg;
        this.name = name;
        this.fileTimestamps = computeFileTimestamps();
        this.relevantFiles = new ArrayList<>();

        if (verbose) {
            System.out.println("Initial state:----------------------");
            for (Map.Entry<String, Long> e : fileTimestamps.entrySet()) {
                System.out.println(e.getKey() + ": " + e.getValue());
            }
            System.out.println("---------------------------------");
        }
        createSnapshot();
        store();
    }

    // reload current_transaction.t
    public TransactionNoBuffering(String name) {
        try {
            FileInputStream fin = new FileInputStream(name + ".t");
            ObjectInputStream in = new ObjectInputStream(fin);
            TransactionNoBuffering t = (TransactionNoBuffering) in.readObject();

            this.cfg = t.cfg;
            this.name = t.name;
            this.fileTimestamps = t.fileTimestamps;
            this.startedCommit = t.startedCommit;
            this.relevantFiles = t.relevantFiles;

            in.close();
            fin.close();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
