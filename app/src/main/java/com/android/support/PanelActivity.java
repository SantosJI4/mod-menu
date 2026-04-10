package com.android.support;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PanelActivity extends Activity {

    private static final String SCRIPT_PATH = "/data/local/tmp/ptr_inject.sh";

    private TextView tvRootStatus, tvScriptStatus, tvLog;
    private ScrollView scrollLog;
    private Button btnStart;
    private SharedPreferences prefs;
    private boolean hasRoot = false;
    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_panel);

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        tvRootStatus = findViewById(R.id.tvRootStatus);
        tvScriptStatus = findViewById(R.id.tvScriptStatus);
        tvLog = findViewById(R.id.tvLog);
        scrollLog = (ScrollView) tvLog.getParent();
        btnStart = findViewById(R.id.btnStart);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFullInjection();
            }
        });

        log("INFO", "App iniciado");
        log("INFO", "Versao: 1.0");
        log("INFO", "Servidor: jawmodsuser.squareweb.app");
        checkRoot();
        checkScript();
    }

    private void log(final String tag, final String msg) {
        final String ts = sdf.format(new Date());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvLog.append("\n[" + ts + "] [" + tag + "] " + msg);
                scrollLog.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollLog.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    private void setButtonState(final boolean enabled, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnStart.setEnabled(enabled);
                btnStart.setText(text);
            }
        });
    }

    private void checkRoot() {
        log("SYS", "Verificando acesso root...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                hasRoot = RootHelper.isRooted();
                if (hasRoot) {
                    log("SYS", "Root: ATIVO");
                    // Pegar info do dispositivo
                    String info = RootHelper.executeAsRoot(
                            "echo \"Arch: $(getprop ro.product.cpu.abi)\" && " +
                            "echo \"Android: $(getprop ro.build.version.release)\" && " +
                            "echo \"SDK: $(getprop ro.build.version.sdk)\" && " +
                            "echo \"Device: $(getprop ro.product.model)\""
                    );
                    if (info != null) {
                        for (String line : info.split("\n")) {
                            if (!line.trim().isEmpty() && !line.startsWith("[EXIT]")) {
                                log("SYS", line.trim());
                            }
                        }
                    }
                } else {
                    log("ERR", "Root NAO encontrado - app requer root");
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (hasRoot) {
                            tvRootStatus.setText("Root Ativo");
                            tvRootStatus.setTextColor(0xFF00FF41);
                        } else {
                            tvRootStatus.setText("Sem Root");
                            tvRootStatus.setTextColor(0xFFFF4444);
                            btnStart.setEnabled(false);
                        }
                    }
                });
            }
        }).start();
    }

    private void checkScript() {
        File f = new File(SCRIPT_PATH);
        if (f.exists()) {
            tvScriptStatus.setText("Disponivel");
            tvScriptStatus.setTextColor(0xFF00FF41);
            log("SYS", "Script encontrado: " + SCRIPT_PATH + " (" + f.length() + " bytes)");
        } else {
            tvScriptStatus.setText("Sera baixado ao iniciar");
            tvScriptStatus.setTextColor(0xFFFFAA00);
            log("SYS", "Script ainda nao baixado");
        }
    }

    private void startFullInjection() {
        if (!hasRoot) {
            log("ERR", "Root necessario");
            return;
        }

        setButtonState(false, "BAIXANDO...");
        tvLog.setText("");
        log("===", "INICIO DA EXECUCAO");

        final String key = prefs.getString("validated_key", "");
        final String serverUrl = "https://jawmodsuser.squareweb.app";
        final long startTime = System.currentTimeMillis();

        new Thread(new Runnable() {
            @Override
            public void run() {
                // --- ETAPA 1: Download ---
                log("NET", "Conectando ao servidor...");
                log("NET", "URL: " + serverUrl + "/api/download/script");

                String tempPath = getCacheDir().getAbsolutePath() + "/ptr_inject.sh";
                long dlStart = System.currentTimeMillis();
                String dlResult = NetworkHelper.downloadScript(serverUrl, key, tempPath);
                long dlTime = System.currentTimeMillis() - dlStart;

                if (!"OK".equals(dlResult)) {
                    log("ERR", "Falha no download: " + dlResult);
                    log("NET", "Tempo: " + dlTime + "ms");
                    setButtonState(true, "INICIAR");
                    return;
                }

                File tempFile = new File(tempPath);
                log("NET", "Download OK (" + tempFile.length() + " bytes em " + dlTime + "ms)");

                // --- ETAPA 2: Instalar com root ---
                setButtonState(false, "PREPARANDO...");
                log("ROOT", "Copiando para " + SCRIPT_PATH + "...");

                String cpResult = RootHelper.executeAsRoot(
                        "cp " + tempPath + " " + SCRIPT_PATH +
                        " && sed -i 's/\\r$//' " + SCRIPT_PATH +
                        " && chmod 755 " + SCRIPT_PATH +
                        " && chown root:root " + SCRIPT_PATH
                );

                if (cpResult.contains("Erro")) {
                    log("ERR", "Falha ao instalar: " + cpResult.trim());
                    setButtonState(true, "INICIAR");
                    return;
                }
                log("ROOT", "Script instalado e permissoes 755 aplicadas");

                // Verificar arquivo instalado
                String verify = RootHelper.executeAsRoot(
                        "ls -la " + SCRIPT_PATH + " && file " + SCRIPT_PATH + " 2>/dev/null && head -1 " + SCRIPT_PATH
                );
                if (verify != null) {
                    for (String line : verify.split("\n")) {
                        if (!line.trim().isEmpty() && !line.startsWith("[EXIT]")) {
                            log("ROOT", "  " + line.trim());
                        }
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        checkScript();
                    }
                });

                // --- ETAPA 3: Executar ---
                setButtonState(false, "EXECUTANDO...");
                log("EXEC", "Executando: sh " + SCRIPT_PATH);
                log("===", "─── SAIDA DO SCRIPT ───");

                long execStart = System.currentTimeMillis();

                RootHelper.executeAsRoot("sh " + SCRIPT_PATH, new RootHelper.LineCallback() {
                    @Override
                    public void onLine(String line) {
                        if (!line.trim().isEmpty()) {
                            log("EXEC", line);
                        }
                    }
                });

                long execTime = System.currentTimeMillis() - execStart;
                long totalTime = System.currentTimeMillis() - startTime;

                log("===", "─── FIM DO SCRIPT ───");
                log("SYS", "Tempo de execucao: " + execTime + "ms");
                log("SYS", "Tempo total: " + totalTime + "ms");
                log("===", "PROCESSO FINALIZADO");

                setButtonState(true, "INICIAR");
            }
        }).start();
    }
}
