package io.github.sakurainakeipoi.hapticboost;

import java.nio.file.Files;
import java.nio.file.Paths;

/** Dependency-free regression tests: run using tools/test-app.ps1. */
public final class AppProtocolTest {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("child")) {
            System.out.println("child-output");
            if (args[1].equals("hang")) Thread.sleep(10000);
            System.exit(args[1].equals("fail") ? 7 : 0);
        }
        String config = new String(Files.readAllBytes(Paths.get("module/config.sh")), "UTF-8");
        check(ModuleProtocol.parse(config) != null, "existing module config accepted");
        check(ModuleProtocol.parse(config.replace("RING_DUR=1000", "RING_DUR=0")) == null, "invalid timing rejected");
        check(ModuleProtocol.parse(config.replace("NORMAL_VMAX=5500", "NORMAL_VMAX=99999999999999999")) == null, "overflow rejected");
        check(ModuleProtocol.parse(config.replace("RING_PLAY=1", "DELETED=1")) == null, "partial config rejected");
        check(ModuleProtocol.parse(config + "\nRING_DUR=1000\n") == null, "duplicate field rejected");
        check(ModuleProtocol.sameConfiguration(config, config.replace("\n", "\r\n")), "line endings ignored");
        String output = "HB_CONFIG_SAVED\n" + config + "\nHB_vmax=5500\nHB_fifo_vmax=4800\nHB_cl_vmax=1280\n";
        check(ModuleProtocol.verified(config, output), "complete readback accepted");
        check(!ModuleProtocol.verified(config, output.replace("HB_vmax=5500", "HB_vmax=1280")), "driver ignoring a write rejected");
        check(!ModuleProtocol.verified(config, output.replace("HB_cl_vmax=1280", "")), "missing node rejected");
        check(!ModuleProtocol.verified(config, output.replace("RING_DUR=1000", "RING_DUR=500")), "config mismatch rejected");
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String cp = System.getProperty("java.class.path");
        String name = AppProtocolTest.class.getName();
        check(ShellRunner.run(3000, java, "-cp", cp, name, "child", "ok").equals("child-output"), "stdout read fully");
        check(ShellRunner.run(3000, java, "-cp", cp, name, "child", "fail").contains("[exit=7]"), "nonzero exit preserved");
        long start = System.nanoTime();
        check(ShellRunner.run(500, java, "-cp", cp, name, "child", "hang").equals("ERR:TIMEOUT"), "blocked command times out");
        check((System.nanoTime() - start) / 1000000 < 3000, "timeout actually bounds elapsed time");
        System.out.println("All 14 app protocol checks passed.");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        System.out.println("PASS: " + name);
    }
}
