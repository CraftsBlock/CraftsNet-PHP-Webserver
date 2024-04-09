package de.craftsblock.craftsnet.webserver;

import de.craftsblock.craftsnet.logging.Logger;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PHPBridge {

    private static final Logger logger = PHPWebserver.getInstance().logger();
    private static final ExecutorService executors = Executors.newFixedThreadPool(4);
    private static final ConcurrentLinkedQueue<Process> processes = new ConcurrentLinkedQueue<>();

    public static void startServer(File datafolder, String downloadUrl) {
        String executable = PHPBridge.downloadAndCompile(datafolder, downloadUrl);

        File live_folder = new File(datafolder, "live");
        live_folder.mkdirs();
        try {
            if (!PHPWebserver.getConfig().contains("php.server.log")) {
                PHPWebserver.getConfig().set("php.server.log", false);
                PHPWebserver.saveConfig();
            }
            runCommand(executable + " -S 0.0.0.0:9000", live_folder, PHPWebserver.getConfig().getBoolean("php.server.log"), true, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void stopServer() throws InterruptedException {
        executors.shutdown();
        if (!executors.awaitTermination(10, TimeUnit.SECONDS)) executors.shutdownNow();

        for (Process process : processes) {
            if (process.isAlive()) process.destroy();
            processes.remove(process);
        }
    }

    public static String downloadAndCompile(File datafolder, String downloadUrl) {
        File binDirectory = new File(datafolder, "php");

        if (!binDirectory.exists())
            try {
                File downloaded = downloadRaw(datafolder, downloadUrl);
                File buildDirectory = downloaded.getParentFile();
                runCommand("tar -xvzf " + downloaded.getName() + " --strip-components=1", buildDirectory, true);

                downloaded.delete();

                runCommand(
                        "./configure --prefix=" + binDirectory.getAbsolutePath() + " --enable-mbstring --with-curl --with-openssl --with-xmlrpc --enable-soap --enable-zip --with-gd --with-jpeg-dir --with-png-dir --with-mysqli --with-pgsql --enable-embedded-mysqli --with-freetype-dir --with-ldap --enable-intl --with-xsl --with-zlib",
                        buildDirectory,
                        true
                );

                runCommand("make", buildDirectory, true);
//                runCommand("make test", buildDirectory, (process, line, error) -> {
//                    if (line.contains("Do you want to save this report in a file"))
//                        process.getOutputStream().write("n\n".getBytes(StandardCharsets.UTF_8));
//                });
                binDirectory.mkdirs();
                runCommand("make install", buildDirectory, true);
                buildDirectory.delete();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }

        return binDirectory.getAbsolutePath() + "/bin/php";
    }

    private static void runCommand(String command, File buildDirectory, boolean log) throws IOException, InterruptedException {
        runCommand(command, buildDirectory, log, null, true, false);
    }

    private static void runCommand(String command, File buildDirectory, boolean log, boolean await) throws IOException, InterruptedException {
        runCommand(command, buildDirectory, log, null, await, false);
    }

    private static void runCommand(String command, File buildDirectory, boolean log, LogCallback callback) throws IOException {
        runCommand(command, buildDirectory, log, callback, true, false);
    }

    private static void runCommand(String command, File buildDirectory, boolean log, boolean await, boolean async) throws IOException {
        runCommand(command, buildDirectory, log, null, await, async);
    }

    private static void runCommand(String command, File buildDirectory, boolean log, LogCallback callback, boolean async) throws IOException {
        runCommand(command, buildDirectory, log, callback, true, async);
    }

    private static void runCommand(String command, File buildDirectory, boolean log, LogCallback callback, boolean await, boolean async) throws IOException {
        ProcessBuilder cmd = new ProcessBuilder(command.split("\\s+"));
        cmd.directory(buildDirectory);

        LogCallback rootCallback = (process, line, error) -> {
            if (callback != null)
                callback.log(process, line, error);

            if (!log) return;
            if (error) logger.error(line);
            else logger.debug(line);
        };

        if (await) runAndWaitWithCallback(cmd, rootCallback, async);
        else runWithCallback(cmd, rootCallback);
    }

    private static void runAndWaitWithCallback(ProcessBuilder processBuilder, LogCallback callback, boolean async) {
        Runnable runnable = () -> {
            try {
                runWithCallback(processBuilder, callback).waitFor();
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        };

        if (async) new Thread(runnable).start();
        else runnable.run();
    }

    private static Process runWithCallback(ProcessBuilder processBuilder, LogCallback callback) throws IOException {
        Process process = processBuilder.start();

        executors.submit(() -> createLogReader(process, process.getInputStream(), callback, false));
        executors.submit(() -> createLogReader(process, process.getErrorStream(), callback, true));
        processes.add(process);

        return process;
    }

    private static void createLogReader(Process process, InputStream stream, LogCallback callback, boolean error) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) callback.log(process, line, error);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static File downloadRaw(File datafolder, String downloadUrl) {
        File folder = new File(datafolder, "temp");
        File destination = new File(folder, downloadUrl.split("/")[downloadUrl.split("/").length - 1]);

        destination.getParentFile().mkdirs();

        try (BufferedInputStream in = new BufferedInputStream(new URI(downloadUrl).toURL().openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(destination)) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, dataBuffer.length)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }

        return destination;
    }

    private interface LogCallback {

        void log(Process process, String line, boolean error) throws IOException;

    }

}
