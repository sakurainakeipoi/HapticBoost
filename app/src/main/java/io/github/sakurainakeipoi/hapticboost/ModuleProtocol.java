package io.github.sakurainakeipoi.hapticboost;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** File/readback validation shared by the editor and save operation. */
final class ModuleProtocol {
    static final String MOD = "/data/adb/modules/haptic_boost_qcom";
    private static final String[] KEYS = {"NORMAL_VMAX", "NORMAL_FIFO_VMAX", "NORMAL_CL_VMAX",
            "CALL_VMAX", "CALL_FIFO_VMAX", "CALL_CL_VMAX", "CALL_BOOST", "POLL", "HOLD",
            "HINT_WINDOW", "RING_PLAY", "RING_DUR", "RING_GAP"};

    static Map<String, Integer> parse(String text) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String key : KEYS) {
            Matcher matcher = Pattern.compile("(?m)^[ \\t]*" + key + "[ \\t]*=[ \\t]*([0-9]+)[ \\t]*(?:#[^\\r\\n]*)?\\r?$").matcher(text);
            if (!matcher.find()) return null;
            int value;
            try { value = Integer.parseInt(matcher.group(1)); }
            catch (NumberFormatException e) { return null; }
            if (matcher.find()) return null;
            int min = key.equals("POLL") ? 1 : key.equals("RING_DUR") ? 100 : 0;
            int max = key.endsWith("VMAX") ? 9500
                    : key.equals("CALL_BOOST") || key.equals("RING_PLAY") ? 1
                    : key.equals("RING_DUR") || key.equals("RING_GAP") ? 3000 : Integer.MAX_VALUE;
            if (value < min || value > max) return null;
            values.put(key, value);
        }
        return values;
    }

    static boolean sameConfiguration(String first, String second) {
        Map<String, Integer> values = parse(first);
        return values != null && values.equals(parse(second));
    }

    static String saveCommand(String encoded) {
        return "[ ! -f /data/local/tmp/vib_ring_active ] || { echo HB_CALL_BUSY; exit 1; }; "
                + "[ -d " + MOD + " ] && [ -f " + MOD + "/apply.sh ] || { echo HB_NO_MODULE; exit 1; }; "
                + "for n in vmax fifo_vmax cl_vmax; do [ -w /sys/class/qcom-haptics/$n ] || { echo HB_NODE_UNAVAILABLE; exit 1; }; done; "
                + "tmp=" + MOD + "/.app-config-$$; trap 'rm -f \"$tmp\"' EXIT; "
                + "printf '%s' '" + encoded + "' | base64 -d > \"$tmp\" && [ -s \"$tmp\" ] "
                + "&& chmod 0644 \"$tmp\" && mv -f \"$tmp\" " + MOD + "/config.sh "
                + "|| { echo HB_WRITE_FAILED; exit 1; }; echo HB_CONFIG_SAVED; "
                + "[ ! -f /data/local/tmp/vib_ring_active ] || { echo HB_CALL_BUSY; exit 1; }; "
                + "sh " + MOD + "/apply.sh normal || { echo HB_APPLY_FAILED; exit 1; }; "
                + "cat " + MOD + "/config.sh || exit 1; "
                + "for n in vmax fifo_vmax cl_vmax; do v=$(cat /sys/class/qcom-haptics/$n) || exit 1; printf 'HB_%s=%s\\n' \"$n\" \"$v\"; done";
    }

    static boolean verified(String expected, String output) {
        if (!sameConfiguration(expected, output)) return false;
        Map<String, Integer> values = parse(expected);
        String[] nodes = {"vmax", "fifo_vmax", "cl_vmax"};
        String[] keys = {"NORMAL_VMAX", "NORMAL_FIFO_VMAX", "NORMAL_CL_VMAX"};
        for (int i = 0; i < nodes.length; i++) {
            Matcher readback = Pattern.compile("(?m)^HB_" + nodes[i] + "=([^\\r\\n]*)\\r?$").matcher(output);
            if (!readback.find() || !readback.group(1).equals(String.valueOf(values.get(keys[i])))
                    || readback.find()) return false;
        }
        return true;
    }

    static String preparePreview(int vmax, int fifo, int cl) {
        int[] values = {vmax, fifo, cl};
        String[] nodes = {"vmax", "fifo_vmax", "cl_vmax"};
        StringBuilder command = new StringBuilder(
                "[ ! -f /data/local/tmp/vib_ring_active ] || { echo HB_CALL_BUSY; exit 1; }; "
                + "[ -f " + MOD + "/config.sh ] && [ -f " + MOD + "/apply.sh ] || { echo HB_NO_MODULE; exit 1; }; ");
        for (int i = 0; i < nodes.length; i++) {
            if (values[i] < 0 || values[i] > 9500) throw new IllegalArgumentException("Invalid voltage");
            String path = "/sys/class/qcom-haptics/" + nodes[i];
            command.append("echo ").append(values[i]).append(" > ").append(path)
                    .append(" && [ \"$(cat ").append(path).append(")\" = ").append(values[i])
                    .append(" ] || { echo HB_PREVIEW_WRITE_FAILED; exit 1; }; ");
        }
        return command.append("echo HB_PREVIEW_READY").toString();
    }

    static String restoreAfterPreview() {
        // A real incoming call may have started during the preview.
        return "mode=normal; [ ! -f /data/local/tmp/vib_ring_active ] || mode=call; "
                + "sh " + MOD + "/apply.sh \"$mode\" || exit 1; "
                + ". " + MOD + "/config.sh || exit 1; "
                + "if [ \"$mode\" = call ]; then v=$CALL_VMAX; f=$CALL_FIFO_VMAX; c=$CALL_CL_VMAX; "
                + "else v=$NORMAL_VMAX; f=$NORMAL_FIFO_VMAX; c=$NORMAL_CL_VMAX; fi; "
                + "[ \"$(cat /sys/class/qcom-haptics/vmax)\" = \"$v\" ] "
                + "&& [ \"$(cat /sys/class/qcom-haptics/fifo_vmax)\" = \"$f\" ] "
                + "&& [ \"$(cat /sys/class/qcom-haptics/cl_vmax)\" = \"$c\" ] "
                + "|| { echo HB_RESTORE_FAILED; exit 1; }";
    }
}
