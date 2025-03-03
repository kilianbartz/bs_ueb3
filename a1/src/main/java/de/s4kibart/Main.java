package de.s4kibart;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class Main {
    public static void main(String[] args) {
        File configToml = new File("config.toml");
        if(!configToml.exists()){
            // generate sample config
            try (InputStream inputStream = Main.class.getResourceAsStream("config.toml")) { // your input stream
                assert inputStream != null;
                Files.copy(inputStream, Paths.get("path/to/file.txt"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.err.println("No config.toml existed. Please configure the newly generated config.toml.");
            System.exit(2);
        }
        if (args.length < 2 || args.length > 3){
            System.err.println("Only 'transaction start' and 'transaction end' are supported as operations");
            System.exit(1);
        }
        if (!args[0].equalsIgnoreCase("transaction")){
            System.err.println("Only 'transaction start' and 'transaction end' are supported as operations");
            System.exit(1);
        }

        Config cfg = new Config("config.toml");
        Transaction t;
        switch(args[1].toLowerCase()){
            case "start":
                // check if another transaction is still running
                File lastTransactionFile = new File(Transaction.LAST_TRANSACTION_FILE);
                if (lastTransactionFile.exists()){
                    System.err.println("There is an old transaction which has not been completed. " +
                            "Rollback or end it first before starting a new transaction.");
                    System.exit(3);
                }
                String name;
                if (args.length == 3){
                    name = args[2];
                } else {
                    //if no name was provided: count up
                    File directory = new File(".");
                    FilenameFilter filter = (dir, n) -> n.startsWith("transaction");
                    String[] matchingFiles = directory.list(filter);
                    int count = matchingFiles != null ? matchingFiles.length : 0;
                    name = "transaction_" + count + ".t";
                }
                t = new Transaction(cfg, name);
                break;
            case "end":
                t = new Transaction();
                t.commit();
                break;
            default:
                System.err.println("Only 'transaction start' and 'transaction end' are supported as operations");
                System.exit(1);
        }
    }
}
