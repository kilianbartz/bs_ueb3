package de.s4kibart;

import java.io.*;
import java.util.*;

public class Transaction implements Serializable {

    Config cfg;
    String name;
    HashMap<String, Long> fileTimestamps;
    HashMap<String, String> writes;
    List<String> removes;

    public void store(){
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

    public boolean commit(){
        boolean conflict = false;
        HashMap<String, Long> currentFileTimestamps = computeFileTimestamps();
        for(Map.Entry<String, Long> e: currentFileTimestamps.entrySet()){
            Long previousTimestamp = fileTimestamps.get(e.getKey());
            if (previousTimestamp == null || !previousTimestamp.equals(e.getValue())){
                conflict = true;
                break;
            }
        }
        if (conflict){
            rollbackSnapshot();
            return false;
        }
        removeSnapshot();
        return true;
    }

    public void write(String path, String content){
        writes.put(path, content);
        store();
    }

    public void remove(String path){
        removes.add(path);
        store();
    }

    public boolean read(String path){
        if (hasConflicts(path)){
            rollbackSnapshot();
            return false;
        }
        StringBuilder content = new StringBuilder();
        try(BufferedReader reader = new BufferedReader(new FileReader(path))){
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

    private void executeCommand(String[] command){
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        try {
            processBuilder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String snapshotName(){
        String fs = cfg.getZfsFilesystem();
        if(!cfg.getFileRoot().isEmpty())
            fs += cfg.getFileRoot();
        return fs + "@" + name;
    }

    private void createSnapshot(){
        String[] command = {"zfs", "snapshot", snapshotName()};
        executeCommand(command);
    }

    private void rollbackSnapshot(){
        String[] command = {"zfs", "rollback", snapshotName()};
        executeCommand(command);
    }

    private void removeSnapshot(){
        String[] command = {"zfs", "destroy", snapshotName()};
        executeCommand(command);
    }

    private HashMap<String, Long> computeFileTimestamps(){
        HashMap<String, Long> temp = new HashMap<>();
        File dir = new File(this.cfg.getFileRoot().isEmpty() ? "." : this.cfg.getFileRoot());
        for (File file: Objects.requireNonNull(dir.listFiles())){
            temp.put(file.getPath(), file.lastModified());
        }
        return temp;
    }

    private boolean hasConflicts(String path){
        File file = new File(path);
        return file.lastModified() != fileTimestamps.get(path);
    }

    public Transaction(Config cfg, String name){
        this.cfg = cfg;
        this.name = name;
        this.fileTimestamps = computeFileTimestamps();
        this.writes = new HashMap<>();
        this.removes = new ArrayList<>();

        createSnapshot();
        store();
    }

    // reload current_transaction.t
    public Transaction(String name){
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
