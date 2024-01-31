package com.kcl;

import com.kcl.extractor.AutoBuild;
import com.semmle.cli2.CodeQL;
import com.semmle.util.exception.UserError;
import com.semmle.util.files.FileUtil;
import com.semmle.util.files.FileUtil8;
import com.semmle.util.io.ZipUtil;
import com.semmle.util.process.Env;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main implements Runnable {
    @CommandLine.Command(
            name = "kcl-extractor",
            description = {"Create and query databases, or work with the QL language."},
            mixinStandardHelpOptions = true
    )

    @CommandLine.Option(names = {"-o", "--output"}, required = true, description = "[Required] The report directory.")
    private Path reportPath;

    @CommandLine.Parameters(description = "[Required] The source code path.")
    private Path sourcePath;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            throw new UserError("Please specify correct source path.");
        }

        if (!Files.exists(reportPath) || !Files.isDirectory(reportPath)) {
            throw new UserError("Please specify correct report path.");
        }

        //for extractor
        Map<String, String> envVars = new LinkedHashMap<>();
        envVars.put("LGTM_SRC", this.sourcePath.toString());
        envVars.put("LGTM_WORKSPACE", this.reportPath.toString());
        envVars.put(Env.Var.TRAP_FOLDER.toString(), this.reportPath.resolve("trap").toString());
        envVars.put(Env.Var.SOURCE_ARCHIVE.toString(), this.reportPath.resolve("src").toString());
        Env.systemEnv().pushEnvironmentContext(envVars);
        try {
            new AutoBuild().run();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //for database
        try {
            Path databasePath = this.reportPath.resolve("database");
            FileUtil8.recursiveDelete(databasePath);
            FileUtil.mkdirs(databasePath.toFile());

            Path projectPath = reportPath.resolve("src");
            Path zipPath = databasePath.resolve("src.zip");
            ZipUtil.zip(zipPath, true, null, Path.of("/"), projectPath);

            String databaseYaml = "codeql-database.yml";
            InputStream databaseStream = getClass().getResourceAsStream("/" + databaseYaml);
            Files.copy(databaseStream, databasePath.resolve(databaseYaml));

            String sourcePrefix = "sourceLocationPrefix: " + projectPath.toString();
            FileWriter fileWriter = new FileWriter(databasePath.resolve(databaseYaml).toFile(), true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            PrintWriter printWriter = new PrintWriter(bufferedWriter);
            printWriter.println(sourcePrefix);
            printWriter.flush();
            bufferedWriter.flush();
            fileWriter.flush();

            Path dbPath = databasePath.resolve("db-kcl");
            FileUtil.mkdirs(dbPath.toFile());

            String dbscheme = "kcl.dbscheme";

            InputStream dbschemeStream = getClass().getResourceAsStream("/" + dbscheme);
            Files.copy(dbschemeStream, reportPath.resolve(dbscheme));

            Path dbschemePath = reportPath.resolve("kcl.dbscheme");
            String[] importCmd = {"dataset", "import", dbPath.toString(), reportPath.resolve("trap").toString(), "-S", dbschemePath.toString()};
            CodeQL.mainApi(importCmd);

            String[] measureCmd = {"dataset", "measure", dbPath.toString(), "-o", dbPath.resolve("kcl.dbscheme.stats").toString()};
            CodeQL.mainApi(measureCmd);

            System.out.println("Database " + databasePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}




