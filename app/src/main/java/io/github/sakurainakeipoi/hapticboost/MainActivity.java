package io.github.sakurainakeipoi.hapticboost;

import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    // Module protocol: keep these paths and the su -c execution contract in sync with the module.
    private static final String MOD = "/data/adb/modules/haptic_boost_qcom";
    private static final String CFG = MOD + "/config.sh";
    private static final int MAX_MV = 9500;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private View contentRoot;
    private MaterialCardView statusCard;
    private TextView statusMode;
    private TextView statusDetail;
    private TextView liveVmax;
    private TextView liveFifo;
    private TextView liveCl;

    private Slider normalVmaxSlider;
    private Slider normalFifoSlider;
    private Slider callVmaxSlider;
    private Slider callFifoSlider;
    private Slider ringDurationSlider;
    private Slider ringGapSlider;

    private TextView normalVmaxValue;
    private TextView normalFifoValue;
    private TextView callVmaxValue;
    private TextView callFifoValue;
    private TextView ringDurationValue;
    private TextView ringGapValue;

    private SwitchMaterial callBoostSwitch;
    private SwitchMaterial ringPlaySwitch;
    private MaterialButton refreshButton;
    private MaterialButton previewButton;
    private MaterialButton applyButton;
    private MaterialButton restoreButton;

    private int normalVmax = 5500;
    private int normalFifoVmax = 4800;
    private int normalClVmax = 1280;
    private int callVmax = 9500;
    private int callFifoVmax = 9500;
    private int callClVmax = 9500;
    private int poll = 1;
    private int hold = 3;
    private int hintWindow = 90;
    private int ringDuration = 1000;
    private int ringGap = 1000;
    private boolean callBoost = true;
    private boolean ringPlay = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_main);

        bindViews();
        bindInteractions();
        renderControls();
        runEntranceMotion();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConfigurationAndStatus();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void bindViews() {
        contentRoot = findViewById(R.id.contentRoot);
        statusCard = findViewById(R.id.statusCard);
        statusMode = findViewById(R.id.statusMode);
        statusDetail = findViewById(R.id.statusDetail);
        liveVmax = findViewById(R.id.liveVmax);
        liveFifo = findViewById(R.id.liveFifo);
        liveCl = findViewById(R.id.liveCl);

        normalVmaxSlider = findViewById(R.id.normalVmaxSlider);
        normalFifoSlider = findViewById(R.id.normalFifoSlider);
        callVmaxSlider = findViewById(R.id.callVmaxSlider);
        callFifoSlider = findViewById(R.id.callFifoSlider);
        ringDurationSlider = findViewById(R.id.ringDurationSlider);
        ringGapSlider = findViewById(R.id.ringGapSlider);

        normalVmaxValue = findViewById(R.id.normalVmaxValue);
        normalFifoValue = findViewById(R.id.normalFifoValue);
        callVmaxValue = findViewById(R.id.callVmaxValue);
        callFifoValue = findViewById(R.id.callFifoValue);
        ringDurationValue = findViewById(R.id.ringDurationValue);
        ringGapValue = findViewById(R.id.ringGapValue);

        callBoostSwitch = findViewById(R.id.callBoostSwitch);
        ringPlaySwitch = findViewById(R.id.ringPlaySwitch);
        refreshButton = findViewById(R.id.refreshButton);
        previewButton = findViewById(R.id.previewButton);
        applyButton = findViewById(R.id.applyButton);
        restoreButton = findViewById(R.id.restoreButton);

        int[] headingIds = {
                R.id.appTitle,
                R.id.statusMode,
                R.id.dailyHeading,
                R.id.callHeading,
                R.id.ringHeading,
                R.id.dangerHeading
        };
        for (int headingId : headingIds) {
            ViewCompat.setAccessibilityHeading(findViewById(headingId), true);
        }
    }

    private void bindInteractions() {
        bindVoltageSlider(normalVmaxSlider, normalVmaxValue, getString(R.string.normal_vmax_label),
                "vmax", false, value -> normalVmax = value);
        bindVoltageSlider(normalFifoSlider, normalFifoValue, getString(R.string.normal_fifo_label),
                "fifo_vmax", true, value -> normalFifoVmax = value);
        bindVoltageSlider(callVmaxSlider, callVmaxValue, getString(R.string.call_vmax_label),
                "vmax", false, value -> callVmax = value);
        bindVoltageSlider(callFifoSlider, callFifoValue, getString(R.string.call_fifo_label),
                "fifo_vmax", true, value -> callFifoVmax = value);

        ringDurationSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                ringDuration = Math.max(100, Math.round(value));
                renderRingValues();
            }
        });
        ringGapSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                ringGap = Math.max(0, Math.round(value));
                renderRingValues();
            }
        });
        Slider.OnSliderTouchListener ringTouchListener = new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                previewLightPulse();
            }
        };
        ringDurationSlider.addOnSliderTouchListener(ringTouchListener);
        ringGapSlider.addOnSliderTouchListener(ringTouchListener);

        callBoostSwitch.setOnCheckedChangeListener((button, checked) -> callBoost = checked);
        ringPlaySwitch.setOnCheckedChangeListener((button, checked) -> ringPlay = checked);

        refreshButton.setOnClickListener(view -> loadConfigurationAndStatus());
        previewButton.setOnClickListener(view -> previewRingPattern());
        applyButton.setOnClickListener(view -> saveConfiguration(false));
        restoreButton.setOnClickListener(view -> showRestoreConfirmation());
    }

    private void bindVoltageSlider(Slider slider, TextView valueView, String accessibilityLabel,
                                   String node, boolean feedbackPath, IntSetter setter) {
        slider.addOnChangeListener((changedSlider, value, fromUser) -> {
            if (!fromUser) {
                return;
            }
            int rounded = clampVoltage(Math.round(value));
            setter.set(rounded);
            renderVoltageValue(valueView, accessibilityLabel, rounded);
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider touchedSlider) {
            }

            @Override
            public void onStopTrackingTouch(Slider touchedSlider) {
                previewVoltage(node, clampVoltage(Math.round(touchedSlider.getValue())), feedbackPath);
            }
        });
    }

    private void renderControls() {
        normalVmaxSlider.setValue(clampVoltage(normalVmax));
        normalFifoSlider.setValue(clampVoltage(normalFifoVmax));
        callVmaxSlider.setValue(clampVoltage(callVmax));
        callFifoSlider.setValue(clampVoltage(callFifoVmax));
        ringDurationSlider.setValue(clamp(ringDuration, 100, 3000));
        ringGapSlider.setValue(clamp(ringGap, 0, 3000));
        callBoostSwitch.setChecked(callBoost);
        ringPlaySwitch.setChecked(ringPlay);

        renderVoltageValue(normalVmaxValue, getString(R.string.normal_vmax_label), normalVmax);
        renderVoltageValue(normalFifoValue, getString(R.string.normal_fifo_label), normalFifoVmax);
        renderVoltageValue(callVmaxValue, getString(R.string.call_vmax_label), callVmax);
        renderVoltageValue(callFifoValue, getString(R.string.call_fifo_label), callFifoVmax);
        renderRingValues();
    }

    private void renderVoltageValue(TextView view, String label, int value) {
        String display = getString(R.string.value_mv_format, value);
        view.setText(display);
        view.setContentDescription(label + "，" + display);
    }

    private void renderRingValues() {
        String duration = getString(R.string.value_ms_format, ringDuration);
        String gap = getString(R.string.value_ms_format, ringGap);
        ringDurationValue.setText(duration);
        ringDurationValue.setContentDescription(getString(R.string.ring_duration) + "，" + duration);
        ringGapValue.setText(gap);
        ringGapValue.setContentDescription(getString(R.string.ring_gap) + "，" + gap);
    }

    private void loadConfigurationAndStatus() {
        statusMode.setText(R.string.status_loading);
        statusDetail.setText(R.string.status_live_hint);
        refreshButton.setEnabled(false);

        worker.execute(() -> {
            String configText = sh("cat " + CFG);
            String liveText = sh(
                    "grep ' STATE ' /data/local/tmp/vibrate_boost_heartbeat 2>/dev/null | tail -n 1; "
                            + "printf '\\nvmax='; cat /sys/class/qcom-haptics/vmax 2>/dev/null; "
                            + "printf '\\nfifo_vmax='; cat /sys/class/qcom-haptics/fifo_vmax 2>/dev/null; "
                            + "printf '\\ncl_vmax='; cat /sys/class/qcom-haptics/cl_vmax 2>/dev/null"
            );
            boolean configLoaded = parseConfiguration(configText);
            LiveStatus liveStatus = parseLiveStatus(liveText);

            runOnUiThread(() -> {
                refreshButton.setEnabled(true);
                if (configLoaded) {
                    renderControls();
                }
                renderStatus(configLoaded, liveStatus);
            });
        });
    }

    private boolean parseConfiguration(String text) {
        Pattern pattern = Pattern.compile(
                "^\\s*(NORMAL_VMAX|NORMAL_FIFO_VMAX|NORMAL_CL_VMAX|CALL_VMAX|CALL_FIFO_VMAX|CALL_CL_VMAX|CALL_BOOST|POLL|HOLD|HINT_WINDOW|RING_PLAY|RING_DUR|RING_GAP)\\s*=\\s*([0-9]+)",
                Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(text);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String key = matcher.group(1);
            int value = Integer.parseInt(matcher.group(2));
            switch (key) {
                case "NORMAL_VMAX": normalVmax = clampVoltage(value); break;
                case "NORMAL_FIFO_VMAX": normalFifoVmax = clampVoltage(value); break;
                case "NORMAL_CL_VMAX": normalClVmax = clampVoltage(value); break;
                case "CALL_VMAX": callVmax = clampVoltage(value); break;
                case "CALL_FIFO_VMAX": callFifoVmax = clampVoltage(value); break;
                case "CALL_CL_VMAX": callClVmax = clampVoltage(value); break;
                case "CALL_BOOST": callBoost = value == 1; break;
                case "POLL": poll = Math.max(1, value); break;
                case "HOLD": hold = Math.max(0, value); break;
                case "HINT_WINDOW": hintWindow = Math.max(0, value); break;
                case "RING_PLAY": ringPlay = value == 1; break;
                case "RING_DUR": ringDuration = clamp(value, 100, 3000); break;
                case "RING_GAP": ringGap = clamp(value, 0, 3000); break;
                default: break;
            }
        }
        return found;
    }

    private LiveStatus parseLiveStatus(String text) {
        LiveStatus status = new LiveStatus();
        Matcher stateMatcher = Pattern.compile("STATE\\s+(normal|call):").matcher(text);
        if (stateMatcher.find()) {
            status.mode = stateMatcher.group(1);
        }
        Matcher voltageMatcher = Pattern.compile("(?m)^(vmax|fifo_vmax|cl_vmax)=([0-9]+)\\s*$").matcher(text);
        while (voltageMatcher.find()) {
            int value = Integer.parseInt(voltageMatcher.group(2));
            switch (voltageMatcher.group(1)) {
                case "vmax": status.vmax = value; break;
                case "fifo_vmax": status.fifo = value; break;
                case "cl_vmax": status.cl = value; break;
                default: break;
            }
        }
        return status;
    }

    private void renderStatus(boolean configLoaded, LiveStatus status) {
        boolean liveLoaded = status.vmax >= 0 || status.fifo >= 0 || status.cl >= 0;
        if (!configLoaded || !liveLoaded) {
            statusMode.setText(R.string.load_failed);
            statusDetail.setText(R.string.load_failed_detail);
        } else if ("call".equals(status.mode)) {
            statusMode.setText(R.string.status_call);
            statusDetail.setText(R.string.status_live_call);
        } else if ("normal".equals(status.mode)) {
            statusMode.setText(R.string.status_normal);
            statusDetail.setText(R.string.status_live_normal);
        } else {
            statusMode.setText(R.string.status_unknown);
            statusDetail.setText(R.string.status_live_waiting);
        }

        liveVmax.setText(formatLive(status.vmax));
        liveFifo.setText(formatLive(status.fifo));
        liveCl.setText(formatLive(status.cl));
        liveVmax.setContentDescription(getString(R.string.a11y_live_vmax, formatLive(status.vmax)));
        liveFifo.setContentDescription(getString(R.string.a11y_live_fifo, formatLive(status.fifo)));
        liveCl.setContentDescription(getString(R.string.a11y_live_cl, formatLive(status.cl)));
        animateStatusCard();
    }

    private String formatLive(int value) {
        return value >= 0 ? String.valueOf(value) : getString(R.string.not_available);
    }

    private void saveConfiguration(boolean restoring) {
        StringBuilder config = new StringBuilder();
        config.append("# ============================================================\n");
        config.append("#  HapticBoost — App 写入配置\n");
        config.append("# ============================================================\n");
        config.append("NORMAL_VMAX=").append(normalVmax).append("\n");
        config.append("NORMAL_FIFO_VMAX=").append(normalFifoVmax).append("\n");
        config.append("NORMAL_CL_VMAX=").append(normalClVmax).append("\n");
        config.append("CALL_VMAX=").append(callVmax).append("\n");
        config.append("CALL_FIFO_VMAX=").append(callFifoVmax).append("\n");
        config.append("CALL_CL_VMAX=").append(callClVmax).append("\n");
        config.append("CALL_BOOST=").append(callBoost ? 1 : 0).append("\n");
        config.append("POLL=").append(poll).append("\n");
        config.append("HOLD=").append(hold).append("\n");
        config.append("HINT_WINDOW=").append(hintWindow).append("\n");
        config.append("RING_PLAY=").append(ringPlay ? 1 : 0).append("\n");
        config.append("RING_DUR=").append(ringDuration).append("\n");
        config.append("RING_GAP=").append(ringGap).append("\n");

        String encoded = Base64.encodeToString(config.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        setBusy(true);
        worker.execute(() -> {
            String result = sh("echo '" + encoded + "' | base64 -d > " + CFG
                    + " && sh " + MOD + "/apply.sh normal");
            boolean failed = result.startsWith("ERR:") || result.contains("[exit=");
            runOnUiThread(() -> {
                setBusy(false);
                if (failed) {
                    statusMode.setText(R.string.load_failed);
                    statusDetail.setText(result);
                    Toast.makeText(this, R.string.load_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(this, restoring ? R.string.restore_factory : R.string.saved, Toast.LENGTH_SHORT).show();
                loadConfigurationAndStatus();
            });
        });
    }

    private void previewVoltage(String node, int value, boolean feedbackPath) {
        worker.execute(() -> {
            String trigger = feedbackPath ? "cmd vibrator_manager feedback 6"
                    : "cmd vibrator_manager synced oneshot 70 96";
            sh("echo " + value + " > /sys/class/qcom-haptics/" + node + "; " + trigger
                    + "; sleep 0.12; sh " + MOD + "/apply.sh normal");
        });
    }

    private void previewLightPulse() {
        worker.execute(() -> sh("cmd vibrator_manager synced oneshot 45 72"));
    }

    private void previewRingPattern() {
        int duration = ringDuration;
        int gap = ringGap;
        boolean enabled = ringPlay;
        previewButton.setEnabled(false);
        Toast.makeText(this, R.string.preview_started, Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            if (!enabled) {
                sh("cmd vibrator_manager synced oneshot 300 255");
            } else {
                StringBuilder command = new StringBuilder();
                command.append("echo 9500 > /sys/class/qcom-haptics/vmax;");
                command.append("echo 9500 > /sys/class/qcom-haptics/fifo_vmax;");
                command.append("echo 9500 > /sys/class/qcom-haptics/cl_vmax;");
                for (int i = 0; i < 3; i++) {
                    command.append("cmd vibrator_manager synced oneshot ").append(duration).append(" 255;");
                    command.append("sleep ").append(seconds(gap)).append(";");
                }
                command.append("sh ").append(MOD).append("/apply.sh normal");
                sh(command.toString());
            }
            runOnUiThread(() -> {
                previewButton.setEnabled(true);
                Toast.makeText(this, R.string.preview_complete, Toast.LENGTH_SHORT).show();
                loadConfigurationAndStatus();
            });
        });
    }

    private void showRestoreConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restore_dialog_title)
                .setMessage(R.string.restore_dialog_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.restore_confirm, (dialog, which) -> restoreFactoryValues())
                .show();
    }

    private void restoreFactoryValues() {
        normalVmax = 1280;
        normalFifoVmax = 7700;
        normalClVmax = 1280;
        callVmax = 9500;
        callFifoVmax = 9500;
        callClVmax = 9500;
        callBoost = false;
        ringPlay = false;
        ringDuration = 1000;
        ringGap = 1000;
        renderControls();
        saveConfiguration(true);
    }

    private void setBusy(boolean busy) {
        applyButton.setEnabled(!busy);
        restoreButton.setEnabled(!busy);
        refreshButton.setEnabled(!busy);
        applyButton.setText(busy ? R.string.saving_settings : R.string.apply_settings);
    }

    private String sh(String command) {
        try {
            Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) > 0) {
                    output.write(buffer, 0, count);
                }
            }
            int exitCode = process.waitFor();
            String text = output.toString(StandardCharsets.UTF_8.name()).trim();
            if (exitCode != 0) {
                return text + " [exit=" + exitCode + "]";
            }
            return text;
        } catch (Throwable throwable) {
            return "ERR: " + throwable;
        }
    }

    private void runEntranceMotion() {
        if (!animationsEnabled()) {
            return;
        }
        contentRoot.setAlpha(0f);
        contentRoot.setTranslationY(dp(12));
        contentRoot.animate().alpha(1f).translationY(0f).setDuration(320L).start();
    }

    private void animateStatusCard() {
        if (!animationsEnabled()) {
            return;
        }
        statusCard.animate().cancel();
        statusCard.setAlpha(0.82f);
        statusCard.setScaleX(0.99f);
        statusCard.setScaleY(0.99f);
        statusCard.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L).start();
    }

    private boolean animationsEnabled() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int clampVoltage(int value) {
        int clamped = clamp(value, 0, MAX_MV);
        return Math.round(clamped / 10f) * 10;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String seconds(int milliseconds) {
        return String.format(Locale.US, "%.3f", milliseconds / 1000f);
    }

    private interface IntSetter {
        void set(int value);
    }

    private static final class LiveStatus {
        String mode;
        int vmax = -1;
        int fifo = -1;
        int cl = -1;
    }
}
