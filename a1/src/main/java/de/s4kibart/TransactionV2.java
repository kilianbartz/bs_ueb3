package de.s4kibart;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class TransactionV2 implements Serializable {

    Config cfg;
    String name;
    HashMap<String, Long> fileTimestamps;

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

    public boolean hasConflict() {
        HashMap<String, Long> currentFileTimestamps = computeFileTimestamps();
        System.out.println("Transaction " + name + ": ------------------");
        for (Map.Entry<String, Long> e : currentFileTimestamps.entrySet()) {
            Long previousTimestamp = fileTimestamps.get(e.getKey());
            System.out.println(e.getKey() + ": " + previousTimestamp + " / " + e.getValue());
            if (previousTimestamp == null || !previousTimestamp.equals(e.getValue())) {
                System.out.println("Transaction " + name + " has conflict with: " + e.getKey());
                return true;
            }
        }
        System.out.println("Transaction " + name + ": no conflict.");
        System.out.println("----------------------------------");
        return false;
    }

    public boolean commit() {
        if (hasConflict()) {
            rollbackSnapshot();
            return false;
        }

        // persist changes
        File dest = new File(headPath());
        File source = new File(snapshotTempDir());
        // delete old contents
        try {
            for (File content : Objects.requireNonNull(dest.listFiles())) {
                FileUtils.forceDelete(content);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // overwrite with new contents
        for (File f : Objects.requireNonNull(source.listFiles())) {
            if (f.isFile()) {
                try {
                    Files.copy(f.toPath(), new File(dest, f.getName()).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        removeSnapshot();
        return true;
    }

    public void write(String path, String content) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(snapshotTempDir() + "/" + path))) {
            writer.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void remove(String path) {
        File file = new File(snapshotTempDir() + "/" + path);
        file.delete();
    }

    public String read(String path) {
        if (hasConflict()) {
            rollbackSnapshot();
            return null;
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
        return content.toString();
    }

    private String headPath() {
        String fs = cfg.getZfsFilesystem();
        if (!cfg.getFileRoot().isEmpty())
            fs += cfg.getFileRoot();
        return fs;
    }

    private String snapshotTempDir() {
        String fs = cfg.getZfsFilesystem();
        return fs + "/" + name;
    }

    private void removeSnapshot() {
        rollbackSnapshot();
    }

    private void createSnapshot() {
        removeSnapshot();
        System.out.println(snapshotTempDir());
        File dir = new File(snapshotTempDir());
        if (!dir.mkdirs()) {
            System.err.println("could not make dir");
            return;
        }
        File headDir = new File(headPath());
        for (File f : Objects.requireNonNull(headDir.listFiles())) {
            if (f.isFile()) {
                try {
                    Files.copy(f.toPath(), new File(dir, f.getName()).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void rollbackSnapshot() {
        File file = new File(snapshotTempDir());
        try {
            FileUtils.forceDelete(file);
        } catch (IOException e) {
            // ignore
        }
    }

    private HashMap<String, Long> computeFileTimestamps() {
        HashMap<String, Long> temp = new HashMap<>();
        String fsRoot = headPath();
        File dir = new File(fsRoot);
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            temp.put(file.getPath(), file.lastModified());
        }
        return temp;
    }

    public TransactionV2(Config cfg, String name) {
        this.cfg = cfg;
        this.name = name;
        createSnapshot();
        this.fileTimestamps = computeFileTimestamps();

        System.out.println("Initial state:----------------------");
        for (Map.Entry<String, Long> e : fileTimestamps.entrySet()) {
            System.out.println(e.getKey() + ": " + e.getValue());
        }
        System.out.println("---------------------------------");

        store();
    }

    // reload current_transaction.t
    public TransactionV2(String name) {
        try {
            FileInputStream fin = new FileInputStream(name);
            ObjectInputStream in = new ObjectInputStream(fin);
            Transaction t = (Transaction) in.readObject();

            this.cfg = t.cfg;
            this.name = t.name;
            this.fileTimestamps = t.fileTimestamps;

            in.close();
            fin.close();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
