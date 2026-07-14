package com.potato.updater.util;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UpdaterErrorLogger {

    private static Path logDir;

    public static void initialize(Path gameCorePath) {
        logDir = gameCorePath.resolve("A_Potato_Updater").resolve("updater_error_log");
    }

    public static void logError(String contextMessage, Throwable e) {
        if (logDir == null) return;

        try {
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File logFile = logDir.resolve("report_" + timestamp + ".txt").toFile();

            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                writer.println("==================================================");
                writer.println(" Potato Updater Error Report");
                writer.println(" Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                writer.println(" Context: " + contextMessage);
                writer.println("==================================================");
                if (e != null) {
                    e.printStackTrace(writer);
                } else {
                    writer.println("No exception stacktrace provided.");
                }
                writer.println();
            }

            System.err.println("[UpdaterErrorLogger] A critical error report has been generated at: " + logFile.getAbsolutePath());
        } catch (Exception ex) {
            System.err.println("[UpdaterErrorLogger] Failed to write error log!");
            ex.printStackTrace();
        }
    }
}
