package id.krdrx.powerhub;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSION = 1001;
    private TextView connectionStatus;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Shizuku.UserServiceArgs serviceArgs;

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
        (requestCode, grantResult) -> updateStatus();

    private static class Action {
        final String title, detail, command;
        Action(String title, String detail, String command) {
            this.title = title; this.detail = detail; this.command = command;
        }
    }

    private final Action[] actions = {
        new Action("Reboot", "Restart perangkat", "reboot"),
        new Action("Reboot to recovery", "Restart ke mode recovery", "reboot recovery"),
        new Action("Reboot to bootloader", "Masuk mode fastboot / bootloader", "reboot bootloader"),
        new Action("Power off", "Matikan perangkat sepenuhnya", "reboot -p"),
        new Action("Turn the screen off", "Matikan layar dan kunci perangkat", "input keyevent 26")
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        int savedTheme = getPreferences(MODE_PRIVATE).getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedTheme);
        super.onCreate(savedInstanceState);
        buildScreen();
        serviceArgs = new Shizuku.UserServiceArgs(
            new ComponentName(BuildConfig.APPLICATION_ID, PowerUserService.class.getName()))
            .daemon(false).processNameSuffix("power").tag("powerhub-command-service").version(1);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        updateStatus();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String text, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setTextColor(MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSurface, 0xff202124));
        return view;
    }

    private MaterialButton button(String text, boolean filled) {
        MaterialButton button = new MaterialButton(this, null,
            filled ? com.google.android.material.R.attr.materialButtonStyle :
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        return button;
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(20));
        root.setBackgroundColor(MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSurface, 0xfffafafa));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label("PowerHub", 28, true), new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton themeButton = button("Tema", false);
        themeButton.setOnClickListener(view -> toggleTheme());
        header.addView(themeButton);
        root.addView(header);

        MaterialCardView statusCard = new MaterialCardView(this);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(18);
        statusCard.setLayoutParams(statusParams);
        statusCard.setRadius(dp(18));
        statusCard.setCardElevation(0);
        statusCard.setStrokeWidth(dp(1));
        statusCard.setStrokeColor(0xff00bfa5);

        LinearLayout statusBox = new LinearLayout(this);
        statusBox.setOrientation(LinearLayout.VERTICAL);
        statusBox.setPadding(dp(16), dp(14), dp(16), dp(14));
        connectionStatus = label("Memeriksa Shizuku...", 16, true);
        statusBox.addView(connectionStatus);
        TextView hint = label("Mulai Shizuku, lalu berikan izin kepada PowerHub.", 13, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(5);
        statusBox.addView(hint, hintParams);

        MaterialButton permissionButton = button("Periksa / minta izin Shizuku", true);
        permissionButton.setOnClickListener(view -> requestShizukuPermission());
        LinearLayout.LayoutParams permissionParams = new LinearLayout.LayoutParams(-1, -2);
        permissionParams.topMargin = dp(10);
        statusBox.addView(permissionButton, permissionParams);
        statusCard.addView(statusBox);
        root.addView(statusCard);

        TextView section = label("KONTROL DAYA", 12, true);
        section.setTextColor(0xff009688);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.topMargin = dp(24);
        sectionParams.bottomMargin = dp(8);
        root.addView(section, sectionParams);

        MaterialCardView actionCard = new MaterialCardView(this);
        actionCard.setRadius(dp(18));
        actionCard.setCardElevation(0);
        LinearLayout actionList = new LinearLayout(this);
        actionList.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < actions.length; i++) {
            Action action = actions[i];
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(12), dp(12), dp(12));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(label(action.title, 16, true));
            TextView detail = label(action.detail, 12, false);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
            detailParams.topMargin = dp(3);
            texts.addView(detail, detailParams);
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton runButton = button("Jalankan", false);
            runButton.setOnClickListener(view -> confirmAction(action));
            row.addView(runButton);
            actionList.addView(row);
            if (i < actions.length - 1) {
                android.view.View divider = new android.view.View(this);
                divider.setBackgroundColor(0x33008080);
                actionList.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
        actionCard.addView(actionList);
        root.addView(actionCard);

        TextView footer = label("Perintah restart dan mati perangkat memerlukan konfirmasi. Beberapa firmware dapat membatasi operasi tertentu.", 12, false);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(16);
        root.addView(footer, footerParams);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void toggleTheme() {
        int current = AppCompatDelegate.getDefaultNightMode();
        int next = current == AppCompatDelegate.MODE_NIGHT_YES
            ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        getPreferences(MODE_PRIVATE).edit().putInt("theme", next).apply();
        AppCompatDelegate.setDefaultNightMode(next);
    }

    private void requestShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            new AlertDialog.Builder(this).setTitle("Shizuku belum aktif")
                .setMessage("Buka aplikasi Shizuku dan mulai servicenya. Setelah aktif, kembali ke PowerHub.")
                .setPositiveButton("Mengerti", null).show();
            updateStatus();
            return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Izin Shizuku sudah diberikan", Toast.LENGTH_SHORT).show();
        } else {
            Shizuku.requestPermission(REQUEST_PERMISSION);
        }
        updateStatus();
    }

    private boolean canRun() {
        return Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    }

    private void updateStatus() {
        if (connectionStatus == null) return;
        if (!Shizuku.pingBinder()) connectionStatus.setText("Shizuku tidak aktif");
        else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
            connectionStatus.setText("Shizuku aktif · Izin diberikan");
        else connectionStatus.setText("Shizuku aktif · Izin belum diberikan");
    }

    private void confirmAction(Action action) {
        String message = action.command.equals("input keyevent 26")
            ? "Layar akan dimatikan. Jika kunci layar aktif, perangkat juga akan terkunci."
            : "Jalankan perintah " + action.title + "?";
        new AlertDialog.Builder(this).setTitle(action.title).setMessage(message)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Lanjutkan", (dialog, which) -> execute(action.command)).show();
    }

    private void execute(String command) {
        if (!canRun()) {
            requestShizukuPermission();
            return;
        }
        Toast.makeText(this, "Menjalankan perintah...", Toast.LENGTH_SHORT).show();
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                IUserService remote = IUserService.Stub.asInterface(binder);
                executor.execute(() -> {
                    try {
                        String result = remote.exec(command);
                        runOnUiThread(() -> {
                            if (result != null && !result.trim().isEmpty())
                                Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                        });
                    } catch (Exception exception) {
                        runOnUiThread(() -> showError("Gagal menjalankan perintah: " + exception.getMessage()));
                    } finally {
                        try { Shizuku.unbindUserService(serviceArgs, this, true); }
                        catch (Exception ignored) {}
                    }
                });
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                runOnUiThread(() -> updateStatus());
            }
        };
        try {
            Shizuku.bindUserService(serviceArgs, connection);
        } catch (Exception exception) {
            showError("Tidak dapat menghubungkan layanan Shizuku: " + exception.getMessage());
        }
    }

    private void showError(String message) {
        new AlertDialog.Builder(this).setTitle("PowerHub").setMessage(message)
            .setPositiveButton("OK", null).show();
    }

    @Override protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        executor.shutdownNow();
        super.onDestroy();
    }
}
