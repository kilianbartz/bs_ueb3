package de.s4kibart;

import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Callable;

public class Main {

    public static void main(String[] args) {
        File configToml = new File("transactions_config.toml");
        if (!configToml.exists()) {
            // generate sample config
            try (InputStream inputStream = Main.class.getResourceAsStream("/config.toml")) { // your input stream
                assert inputStream != null;
                Files.copy(inputStream, Paths.get("transactions_config.toml"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.err
                    .println("No config.toml existed. Please configure the newly generated transactions_config.toml.");
            System.exit(2);
        }

        Config cfg = new Config("transactions_config.toml");
        CommandLine commandLine = new CommandLine(new ParentCommand());

        // Add all subcommands
        commandLine.addSubcommand(new TransactionStartCommand(cfg));
        commandLine.addSubcommand(new TransactionCommitCommand());
        commandLine.addSubcommand(new TransactionWriteCommand());
        commandLine.addSubcommand(new TransactionRemoveCommand());
        commandLine.addSubcommand(new TransactionReadCommand());

        // Execute the command
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }
}

@CommandLine.Command(name = "transaction", mixinStandardHelpOptions = true, version = "1.0", description = "Manages file transactions")
class ParentCommand implements Runnable {
    @Override
    public void run() {
        // This code runs when no subcommand is specified
        System.out.println("Please specify a subcommand. Use --help for more information.");
    }
}

@CommandLine.Command(name = "start", mixinStandardHelpOptions = true, version = "1.0", description = "Starts a transaction")
class TransactionStartCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "name of transaction")
    private String name;
    private Config cfg;

    public TransactionStartCommand(Config cfg) {
        this.cfg = cfg;
    }

    // Add a setter for the config
    public void setConfig(Config cfg) {
        this.cfg = cfg;
    }

    @Override
    public void run() {
        new Transaction(cfg, name);
    }
}

@CommandLine.Command(name = "commit", mixinStandardHelpOptions = true, version = "1.0", description = "Commits a transaction if no conflicts were detected and otherwise rolls back to the initial state")
class TransactionCommitCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "name of transaction")
    private String name;

    @Override
    public Integer call() throws Exception {
        return new Transaction(name).commit() ? 0 : 1;
    }
}

@CommandLine.Command(name = "write", mixinStandardHelpOptions = true, version = "1.0", description = "Writes to a path as part of a named transaction")
class TransactionWriteCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "name of transaction")
    private String name;

    @CommandLine.Parameters(index = "1", description = "path of the file to be written to")
    private String path;

    @CommandLine.Parameters(index = "2", description = "content to be written")
    private String content;

    @Override
    public void run() {
        new Transaction(name).write(path, content);
    }
}

@CommandLine.Command(name = "remove", mixinStandardHelpOptions = true, version = "1.0", description = "Removes a file as part of a named transaction")
class TransactionRemoveCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "name of transaction")
    private String name;

    @CommandLine.Parameters(index = "1", description = "path of the file to be removed")
    private String path;

    @Override
    public void run() {
        new Transaction(name).remove(path);
    }
}

@CommandLine.Command(name = "read", mixinStandardHelpOptions = true, version = "1.0", description = "Reads a file as part of a named transaction")
class TransactionReadCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "name of transaction")
    private String name;

    @CommandLine.Parameters(index = "1", description = "path of the file to be removed")
    private String path;

    @Override
    public Integer call() {
        return new Transaction(name).read(path) ? 0 : 1;
    }
}
