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
    boolean verbose = false;

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void store() {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(name);
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
        for (Map.Entry<String, Long> e : currentFileTimestamps.entrySet()) {
            Long previousTimestamp = fileTimestamps.get(e.getKey());
            // System.out.println(e.getKey() + ": " + previousTimestamp + " / " +
            // e.getValue());
            if (previousTimestamp == null || !previousTimestamp.equals(e.getValue())) {
                if (verbose)
                    System.out.println("Transaction " + name + " has conflict with: " + e.getKey());
                return true;
            }
        }
        return false;
    }

    public boolean commit() {
        if (hasConflicts()) {
            rollbackSnapshot();
            return false;
        }
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
        return true;
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

    public boolean read(String path) {
        if (hasConflicts()) {
            rollbackSnapshot();
            return false;
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
        return true;
    }

    private void executeCommand(String[] command) {
        long start = System.currentTimeMillis();
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        try {
            Process process = processBuilder.start();
            int exitcode = process.waitFor();
            if (verbose)
                System.out.println("command " + command[0] + " exited with code " + exitcode + " in "
                        + (System.currentTimeMillis() - start) + "ms");
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
        String[] command = {"zfs", "snapshot", snapshotName()};
        executeCommand(command);
        // System.out.println("created snapshot " + snapshotName());
    }

    private void rollbackSnapshot() {
        if (verbose)
            System.out.println("resetting system to snapshot " + name + "...");
        String[] command = {"zfs", "rollback", snapshotName()};
        executeCommand(command);
        // because the transaction failed, start a new transaction from the new
        File file = new File(name);
        file.delete();
    }

    private void removeSnapshot() {
        String[] command = {"zfs", "destroy", snapshotName()};
        executeCommand(command);
        // also remove the transaction file
        File file = new File(name);
        file.delete();
    }

    private HashMap<String, Long> computeFileTimestamps() {
        HashMap<String, Long> temp = new HashMap<>();
        File dir = new File(headPath());
        File[] files = dir.listFiles();
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
    public Transaction(String name) {
        try {
            FileInputStream fin = new FileInputStream(name);
            ObjectInputStream in = new ObjectInputStream(fin);
            Transaction t = (Transaction) in.readObject();

            this.cfg = t.cfg;
            this.name = t.name;
            this.fileTimestamps = t.fileTimestamps;
            this.writes = t.writes;
            this.removes = t.removes;

            in.close();
            fin.close();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
