package com.claims.platform.e2e;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Starts packaged service jars as child processes and waits until their health endpoint is UP. */
final class ServiceProcesses implements AutoCloseable {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path LOGS = Path.of("target", "e2e-logs").toAbsolutePath();

    private final List<Process> processes = new ArrayList<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    /** @return base URL of the started service */
    String start(String module, Map<String, String> env) {
        Path jar = ROOT.resolve(module).resolve("target").resolve(module + ".jar");
        if (!Files.exists(jar)) {
            throw new IllegalStateException("Missing " + jar + " - run the build with 'verify' so services are packaged first");
        }
        int port = freePort();
        try {
            Files.createDirectories(LOGS);
            String java = ProcessHandle.current().info().command().orElse("java");
            ProcessBuilder builder = new ProcessBuilder(java, "-Xmx384m", "-XX:TieredStopAtLevel=1", "-jar", jar.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(LOGS.resolve(module + ".log").toFile());
            builder.environment().putAll(env);
            builder.environment().put("PORT", String.valueOf(port));
            builder.environment().put("SPRING_PROFILES_ACTIVE", "dev");
            processes.add(builder.start());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return "http://localhost:" + port;
    }

    /** Waits for all given services to report UP (they start in parallel). */
    void awaitHealthy(Duration timeout, String... baseUrls) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        for (String base : baseUrls) {
            while (!healthy(base)) {
                if (System.nanoTime() > deadline) {
                    throw new IllegalStateException(base + " did not become healthy; see " + LOGS);
                }
                for (Process p : processes) {
                    if (!p.isAlive()) {
                        throw new IllegalStateException("A service process exited early; see " + LOGS);
                    }
                }
                Thread.sleep(500);
            }
        }
    }

    private boolean healthy(String base) {
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base + "/actuator/health"))
                    .timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("UP");
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void close() {
        processes.forEach(Process::destroy);
        for (Process p : processes) {
            try {
                if (!p.waitFor(Duration.ofSeconds(15).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                p.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }
}
