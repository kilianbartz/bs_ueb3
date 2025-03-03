package de.s4kibart;

import java.io.*;
import java.util.*;

public class Transaction implements Serializable {

    Config cfg;
    String name;
    HashMap<String, Long> fileTimestamps;
    HashMap<String, String> writes;
    List<String> removes;

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

    public boolean commit() {
        boolean conflict = false;
        HashMap<String, Long> currentFileTimestamps = computeFileTimestamps();
        for (Map.Entry<String, Long> e : currentFileTimestamps.entrySet()) {
            Long previousTimestamp = fileTimestamps.get(e.getKey());
            if (previousTimestamp == null || !previousTimestamp.equals(e.getValue())) {
                conflict = true;
                System.out.println("Conflict found: " + e.getKey());
                break;
            }
        }
        if (conflict) {
            rollbackSnapshot();
            return false;
        }
        removeSnapshot();
        return true;
    }

    public void write(String path, String content) {
        writes.put(path, content);
        store();
    }

    public void remove(String path) {
        removes.add(path);
        store();
    }

    public boolean read(String path) {
        if (fileHasConflicts(path)) {
            rollbackSnapshot();
            return false;
        }
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append(System.lineSeparator());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.print(content);
        return true;
    }

    private void executeCommand(String[] command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        try {
            processBuilder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String snapshotName() {
        String fs = cfg.getZfsFilesystem();
        if (!cfg.getFileRoot().isEmpty())
            fs += cfg.getFileRoot();
        return fs + "@" + name;
    }

    private void createSnapshot() {
        //zfs does not allow overwriting snapshots, so remove a snapshot of the same name if it exists
        removeSnapshot();
        String[] command = {"zfs", "snapshot", snapshotName()};
        executeCommand(command);
    }

    private void rollbackSnapshot() {
        System.out.println("resetting system...");
        String[] command = {"zfs", "rollback", snapshotName()};
        executeCommand(command);
        //because the transaction failed a new one has to be created
//        removeSnapshot();
    }

    private void removeSnapshot() {
        String[] command = {"zfs", "destroy", snapshotName()};
        executeCommand(command);
    }

    private HashMap<String, Long> computeFileTimestamps() {
        HashMap<String, Long> temp = new HashMap<>();
        String fsRoot = "/" + cfg.getZfsFilesystem() + this.cfg.getFileRoot();
        File dir = new File(fsRoot);
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            temp.put(file.getPath(), file.lastModified());
        }
        return temp;
    }

    //only checks for conflicts on a single file
    private boolean fileHasConflicts(String path) {
        File file = new File(path);
        boolean conflict = file.lastModified() != fileTimestamps.get(path);
        System.out.println(path + " has conflict: " + conflict);
        return conflict;
    }

    public Transaction(Config cfg, String name) {
        this.cfg = cfg;
        this.name = name;
        this.fileTimestamps = computeFileTimestamps();
        this.writes = new HashMap<>();
        this.removes = new ArrayList<>();

        System.out.println("Initial state:----------------------");
        for (Map.Entry<String, Long> e : fileTimestamps.entrySet()) {
            System.out.println(e.getKey() + ": " + e.getValue());
        }
        System.out.println("---------------------------------");

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
