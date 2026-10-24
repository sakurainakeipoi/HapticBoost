package io.github.sakurainakeipoi.hapticboost;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Bounded process execution, including a child that keeps stdout open. */
final class ShellRunner {
    static String run(long timeoutMs, String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            Process running = process;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try (InputStream input = running.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        synchronized (output) {
                            if (output.size() < 131072) output.write(buffer, 0, count);
                        }
                    }
                } catch (Exception ignored) { /* Process termination closes its pipe. */ }
            }, "haptic-shell-output");
            reader.setDaemon(true);
            reader.start();
            long deadline = System.nanoTime() + timeoutMs * 1000000L;
            int exitCode;
            while (true) {
                if (System.nanoTime() >= deadline) return "ERR:TIMEOUT";
                try { exitCode = process.exitValue(); break; }
                catch (IllegalThreadStateException runningProcess) { Thread.sleep(25); }
            }
            long remaining = Math.max(1, (deadline - System.nanoTime()) / 1000000L);
            reader.join(remaining);
            if (reader.isAlive()) return "ERR:TIMEOUT";
            synchronized (output) {
                String text = output.toString(StandardCharsets.UTF_8.name()).trim();
                return exitCode == 0 ? text : text + " [exit=" + exitCode + "]";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "ERR:INTERRUPTED";
        } catch (Exception e) {
            return "ERR: " + e;
        } finally {
            if (process != null) process.destroy();
        }
    }
}
