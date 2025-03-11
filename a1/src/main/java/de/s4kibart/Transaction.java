package de.s4kibart;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Transaction implements Serializable {

    Config cfg;
    String name;
    HashMap<String, Long> fileTimestamps;
    HashMap<String, String> writes;
    List<String> removes;
    List<String> relevantFiles;
    boolean verbose = false;
    boolean startedCommit = false;

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

        // persist writes and removes
        for (Map.Entry<String, String> e : writes.entrySet()) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(e.getKey()))) {
                writer.write(e.getValue());
                writer.flush();
                // System.out.println("written: " + e.getKey() + ": " + e.getValue());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        for (String f : removes) {
            File file = new File(f);
            file.delete();
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
        writes.put(headPath() + "/" + path, content);
        store();
    }

    public void remove(String path) {
        removes.add(headPath() + "/" + path);
        store();
    }

    // returns rollback time if fails; else -1
    public long read(String path) {
        if (hasConflicts()) {
            return rollbackSnapshot();
        }
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(headPath() + "/" + path))) {
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

    private long rollbackSnapshot() {
        if (verbose)
            System.out.println("resetting system to snapshot " + name + "...");
        // because no actual writes have been made yet, just abort
        removeSnapshot();
        return 1;
    }

    private void removeSnapshot() {
        // remove the transaction file
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

    public Transaction(Config cfg, String name) {
        this.cfg = cfg;
        this.name = name;
        this.fileTimestamps = computeFileTimestamps();
        this.writes = new HashMap<>();
        this.removes = new ArrayList<>();
        this.relevantFiles = new ArrayList<>();

        if (verbose) {
            System.out.println("Initial state:----------------------");
            for (Map.Entry<String, Long> e : fileTimestamps.entrySet()) {
                System.out.println(e.getKey() + ": " + e.getValue());
            }
            System.out.println("---------------------------------");
        }
        // this implementation does not need zfs snapshots
        // createSnapshot();
        store();
    }

    // reload current_transaction.t
    public Transaction(String name) {
        try {
            FileInputStream fin = new FileInputStream(name + ".t");
            ObjectInputStream in = new ObjectInputStream(fin);
            Transaction t = (Transaction) in.readObject();

            this.cfg = t.cfg;
            this.name = t.name;
            this.fileTimestamps = t.fileTimestamps;
            this.writes = t.writes;
            this.removes = t.removes;
            this.startedCommit = t.startedCommit;
            this.relevantFiles = t.relevantFiles;

            in.close();
            fin.close();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
