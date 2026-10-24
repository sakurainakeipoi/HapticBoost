package io.github.sakurainakeipoi.hapticboost;

import android.animation.ValueAnimator;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
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

    // Keep operations serialized across Activity recreation as well as button taps.
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();

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
    private TextView ringDurationValue;
    private TextView ringGapValue;
    private TextView draftStatus;
    private MaterialButton undoButton;
    private View rhythmShort;
    private View rhythmEven;
    private View rhythmLong;
    private String savedConfiguration;
    private boolean applyUnverified;
    private boolean rendering;
    private boolean loading;

    private TextView normalVmaxValue;
    private TextView normalFifoValue;
    private TextView callVmaxValue;
    private TextView callFifoValue;

    private SwitchMaterial callBoostSwitch;
    private View incomingControls;
    private MaterialButton refreshButton;
    private MaterialButton previewButton;
    private MaterialButton normalPreviewButton;
    private MaterialButton feedbackPreviewButton;
    private MaterialButton applyButton;
    private MaterialButton restoreButton;
    private boolean busy;

    private int normalVmax = 5500;
    private int normalFifoVmax = 4800;
    private int normalClVmax = 1280;
    private int callVmax = 9500;
    private int callFifoVmax = 9500;
    private int callClVmax = 9500;
    private int poll = 1;
    private int hold = 0;
    private int hintWindow = 30;
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
        String draft = getPreferences(MODE_PRIVATE).getString("draft", null);
        savedConfiguration = getPreferences(MODE_PRIVATE).getString("baseline", null);
        applyUnverified = getPreferences(MODE_PRIVATE).getBoolean("applyUnverified", false);
        if (draft != null && !parseConfiguration(draft)) savedConfiguration = null;
        renderControls();
        setBusy(false);
        runEntranceMotion();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConfigurationAndStatus();
    }

    @Override
    protected void onPause() {
        persistDraft();
        super.onPause();
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
        ringDurationValue = findViewById(R.id.ringDurationValue);
        ringGapValue = findViewById(R.id.ringGapValue);
        draftStatus = findViewById(R.id.draftStatus);
        undoButton = findViewById(R.id.undoButton);
        rhythmShort = findViewById(R.id.rhythmShort);
        rhythmEven = findViewById(R.id.rhythmEven);
        rhythmLong = findViewById(R.id.rhythmLong);

        normalVmaxValue = findViewById(R.id.normalVmaxValue);
        normalFifoValue = findViewById(R.id.normalFifoValue);
        callVmaxValue = findViewById(R.id.callVmaxValue);
        callFifoValue = findViewById(R.id.callFifoValue);

        callBoostSwitch = findViewById(R.id.callBoostSwitch);
        incomingControls = findViewById(R.id.incomingControls);
        refreshButton = findViewById(R.id.refreshButton);
        previewButton = findViewById(R.id.previewButton);
        normalPreviewButton = findViewById(R.id.normalPreviewButton);
        feedbackPreviewButton = findViewById(R.id.feedbackPreviewButton);
        applyButton = findViewById(R.id.applyButton);
        restoreButton = findViewById(R.id.restoreButton);

        int[] headingIds = {
                R.id.appTitle,
                R.id.statusMode,
                R.id.settingsHeading,
                R.id.dailyHeading,
                R.id.callBoostSwitch,
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

        callBoostSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (rendering) return;
            callBoost = checked;
            ringPlay = checked;
            updateIncomingControls(checked);
            updateDraftStatus();
        });

        bindTimingSlider(ringDurationSlider, ringDurationValue, value -> ringDuration = value);
        bindTimingSlider(ringGapSlider, ringGapValue, value -> ringGap = value);
        rhythmShort.setOnClickListener(view -> setRhythm(250, 500));
        rhythmEven.setOnClickListener(view -> setRhythm(1000, 1000));
        rhythmLong.setOnClickListener(view -> setRhythm(1500, 1000));
        undoButton.setOnClickListener(view -> {
            if (savedConfiguration != null) {
                parseConfiguration(savedConfiguration);
                renderControls();
                persistDraft();
            }
        });

        refreshButton.setOnClickListener(view -> loadConfigurationAndStatus());
        previewButton.setOnClickListener(view -> previewRingPattern());
        normalPreviewButton.setOnClickListener(view -> previewDailyPattern(false));
        feedbackPreviewButton.setOnClickListener(view -> previewDailyPattern(true));
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
            updateDraftStatus();
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider touchedSlider) {
            }

            @Override
            public void onStopTrackingTouch(Slider touchedSlider) {
                runPreview(feedbackPath, touchedSlider == callVmaxSlider || touchedSlider == callFifoSlider, false);
            }
        });
    }

    private void renderControls() {
        rendering = true;
        normalVmaxSlider.setValue(clampVoltage(normalVmax));
        normalFifoSlider.setValue(clampVoltage(normalFifoVmax));
        callVmaxSlider.setValue(clampVoltage(callVmax));
        callFifoSlider.setValue(clampVoltage(callFifoVmax));
        boolean incomingEnabled = callBoost || ringPlay;
        callBoost = incomingEnabled;
        ringPlay = incomingEnabled;
        callBoostSwitch.setChecked(incomingEnabled);
        updateIncomingControls(incomingEnabled);
        ringDurationSlider.setValue(ringDuration);
        ringGapSlider.setValue(ringGap);
        renderTimingValue(ringDurationValue, ringDuration);
        renderTimingValue(ringGapValue, ringGap);

        renderVoltageValue(normalVmaxValue, getString(R.string.normal_vmax_label), normalVmax);
        renderVoltageValue(normalFifoValue, getString(R.string.normal_fifo_label), normalFifoVmax);
        renderVoltageValue(callVmaxValue, getString(R.string.call_vmax_label), callVmax);
        renderVoltageValue(callFifoValue, getString(R.string.call_fifo_label), callFifoVmax);
        rendering = false;
        updateDraftStatus();
    }

    private void bindTimingSlider(Slider slider, TextView valueView, IntSetter setter) {
        slider.addOnChangeListener((view, value, fromUser) -> {
            if (!fromUser) return;
            int milliseconds = Math.round(value);
            setter.set(milliseconds);
            renderTimingValue(valueView, milliseconds);
            updateDraftStatus();
        });
    }

    private void renderTimingValue(TextView valueView, int milliseconds) {
        int label = valueView == ringDurationValue ? R.string.ring_duration_label : R.string.ring_gap_label;
        valueView.setText(getString(R.string.timing_value_format, getString(label), milliseconds));
    }

    private void setRhythm(int duration, int gap) {
        ringDuration = duration;
        ringGap = gap;
        renderControls();
    }

    private boolean hasDraft() {
        return savedConfiguration != null && (applyUnverified
                || !ModuleProtocol.sameConfiguration(configurationText(), savedConfiguration));
    }

    private void updateDraftStatus() {
        draftStatus.setText(applyUnverified ? R.string.apply_unverified
                : hasDraft() ? R.string.unsaved_changes : R.string.no_unsaved_changes);
        undoButton.setEnabled(!busy && !loading && savedConfiguration != null && hasDraft());
    }

    private void persistDraft() {
        getPreferences(MODE_PRIVATE).edit()
                .putString("draft", savedConfiguration == null ? null : configurationText())
                .putString("baseline", savedConfiguration)
                .putBoolean("applyUnverified", applyUnverified).apply();
    }

    private void renderVoltageValue(TextView view, String label, int value) {
        String display = getString(R.string.value_mv_format, value);
        view.setText(display);
        view.setContentDescription(label + "，" + display);
    }

    private void updateIncomingControls(boolean enabled) {
        incomingControls.setAlpha(enabled ? 1f : 0.42f);
        boolean available = enabled && !busy && !loading && savedConfiguration != null;
        callVmaxSlider.setEnabled(available);
        callFifoSlider.setEnabled(available);
        previewButton.setEnabled(available);
        ringDurationSlider.setEnabled(available);
        ringGapSlider.setEnabled(available);
        rhythmShort.setEnabled(available);
        rhythmEven.setEnabled(available);
        rhythmLong.setEnabled(available);
    }

    private void loadConfigurationAndStatus() {
        if (busy || loading) return;
        loading = true;
        setBusy(false);
        statusMode.setText(R.string.status_loading);
        statusDetail.setText(R.string.status_live_hint);
        refreshButton.setEnabled(false);

        worker.execute(() -> {
            String configText = sh("[ -f " + CFG + " ] || { echo HB_NO_MODULE; exit 1; }; cat " + CFG);
            String liveText = sh(
                    "grep ' STATE ' /data/local/tmp/vibrate_boost_heartbeat 2>/dev/null | tail -n 1; "
                            + "printf '\\nvmax='; cat /sys/class/qcom-haptics/vmax 2>/dev/null; "
                            + "printf '\\nfifo_vmax='; cat /sys/class/qcom-haptics/fifo_vmax 2>/dev/null; "
                            + "printf '\\ncl_vmax='; cat /sys/class/qcom-haptics/cl_vmax 2>/dev/null"
            );
            boolean configLoaded = !commandFailed(configText) && ModuleProtocol.parse(configText) != null;
            LiveStatus liveStatus = parseLiveStatus(liveText);

            runOnUiThread(() -> {
                if (isDestroyed()) return;
                loading = false;
                applyUnverified = getPreferences(MODE_PRIVATE).getBoolean("applyUnverified", applyUnverified);
                if (configLoaded) {
                    boolean keepDraft = hasDraft();
                    if (!keepDraft) parseConfiguration(configText);
                    savedConfiguration = configText;
                    renderControls();
                }
                setBusy(busy);
                renderStatus(configLoaded, liveStatus);
                if (!configLoaded) statusDetail.setText(errorMessage(configText));
                persistDraft();
            });
        });
    }

    private boolean parseConfiguration(String text) {
        if (ModuleProtocol.parse(text) == null) return false;
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
                case "HOLD": hold = 0; break;
                case "HINT_WINDOW": hintWindow = clamp(value, 0, 30); break;
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
        boolean liveLoaded = status.vmax >= 0 && status.fifo >= 0 && status.cl >= 0;
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

    private String configurationText() {
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
        config.append("RING_PLAY=").append(callBoost ? 1 : 0).append("\n");
        config.append("RING_DUR=").append(ringDuration).append("\n");
        config.append("RING_GAP=").append(ringGap).append("\n");
        return config.toString();
    }

    private void saveConfiguration(boolean restoring) {
        if (busy || loading) return;
        String snapshot = configurationText();
        String encoded = Base64.encodeToString(snapshot.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        setBusy(true);
        applyButton.setText(R.string.saving_settings);
        worker.execute(() -> {
            String result = sh(ModuleProtocol.saveCommand(encoded));
            boolean failed = commandFailed(result) || !ModuleProtocol.verified(snapshot, result);
            runOnUiThread(() -> {
                // Persist the result even if a configuration change destroyed this Activity.
                applyUnverified = failed;
                if (!failed) savedConfiguration = snapshot;
                persistDraft();
                if (isDestroyed()) return;
                setBusy(false);
                applyButton.setText(R.string.apply_settings);
                if (failed) {
                    statusMode.setText(R.string.save_failed);
                    statusDetail.setText(errorMessage(result));
                    Toast.makeText(this, errorMessage(result), Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, restoring ? R.string.restore_factory : R.string.saved, Toast.LENGTH_SHORT).show();
                loadConfigurationAndStatus();
            });
        });
    }

    private void previewDailyPattern(boolean feedbackPath) {
        runPreview(feedbackPath, false, true);
    }

    private void previewRingPattern() {
        runPreview(false, true, true);
    }

    private void runPreview(boolean feedbackPath, boolean incoming, boolean full) {
        if (busy || loading || savedConfiguration == null) return;
        int vmax = incoming ? callVmax : normalVmax;
        int fifo = incoming ? callFifoVmax : normalFifoVmax;
        int cl = incoming ? callClVmax : normalClVmax;
        long[] timings = incoming ? PreviewPattern.ringtone(full, ringDuration, ringGap)
                : PreviewPattern.notification(full);
        setBusy(true);
        if (full) Toast.makeText(this, R.string.preview_started, Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            String result = sh(ModuleProtocol.preparePreview(vmax, fifo, cl));
            boolean callBusy = result.contains("HB_CALL_BUSY");
            boolean failed = commandFailed(result) || !result.contains("HB_PREVIEW_READY");
            boolean restoreFailed = false;
            try {
                if (!failed) {
                    if (feedbackPath || incoming) {
                        StringBuilder command = new StringBuilder();
                        if (feedbackPath) {
                            for (int i = 0; i < (full ? 3 : 1); i++) {
                                command.append("[ ! -f /data/local/tmp/vib_ring_active ] || exit 1; ")
                                        .append("cmd vibrator_manager feedback 6 || exit 1; sleep 0.3; ");
                            }
                        } else {
                            for (int i = 0; i < timings.length; i += 2) {
                                command.append("sleep ").append(seconds((int) timings[i])).append("; ")
                                        .append("[ ! -f /data/local/tmp/vib_ring_active ] || exit 1; ")
                                        .append("cmd vibrator_manager synced oneshot -a ").append(timings[i + 1])
                                        .append(" 255 || exit 1; ");
                            }
                        }
                        failed = commandFailed(sh(command.toString()));
                    } else {
                        playNotificationPreview(timings);
                        // vibrate() is asynchronous. Keep preview voltages until the waveform ends.
                        Thread.sleep(PreviewPattern.duration(timings) + 150);
                    }
                }
            } catch (Exception exception) {
                failed = true;
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            } finally {
                if (!callBusy) restoreFailed = commandFailed(sh(ModuleProtocol.restoreAfterPreview()));
            }
            final int message = callBusy ? R.string.preview_call_busy
                    : restoreFailed ? R.string.preview_restore_failed
                    : failed ? R.string.preview_failed : R.string.preview_complete;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                setBusy(false);
                if (full || message != R.string.preview_complete) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @SuppressWarnings("deprecation")
    private void playNotificationPreview(long[] timings) {
        Vibrator vibrator = (Vibrator) getApplicationContext().getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) throw new IllegalStateException("No vibrator");
        AudioAttributes audio = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1),
                    new VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_NOTIFICATION).build());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1), audio);
        } else {
            vibrator.vibrate(timings, -1, audio);
        }
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
        this.busy = busy;
        boolean available = !busy && !loading && savedConfiguration != null;
        applyButton.setEnabled(available);
        restoreButton.setEnabled(available);
        refreshButton.setEnabled(!busy && !loading);
        callBoostSwitch.setEnabled(available);
        normalPreviewButton.setEnabled(available);
        feedbackPreviewButton.setEnabled(available);
        normalVmaxSlider.setEnabled(available);
        normalFifoSlider.setEnabled(available);
        updateIncomingControls(callBoost);
        updateDraftStatus();
    }

    private String sh(String command) {
        return ShellRunner.run(30000, "su", "-c",
                "[ \"$(id -u)\" = 0 ] || { echo HB_ROOT_DENIED; exit 1; }; " + command);
    }

    private static boolean commandFailed(String result) {
        return result.startsWith("ERR:") || result.contains("[exit=");
    }

    private int errorMessage(String result) {
        if (result.contains("HB_CALL_BUSY")) return R.string.preview_call_busy;
        if (result.contains("ERR:TIMEOUT")) return R.string.operation_timeout;
        if (result.contains("HB_NO_MODULE")) return R.string.module_missing;
        if (result.contains("HB_NODE_UNAVAILABLE")) return R.string.nodes_unavailable;
        if (result.contains("HB_CONFIG_SAVED")) return R.string.save_verify_failed;
        if (result.contains("HB_WRITE_FAILED")) return R.string.config_write_failed;
        if (commandFailed(result)) return R.string.root_unavailable;
        return R.string.config_invalid;
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
