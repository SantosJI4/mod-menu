package com.android.support;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;

public class PanelActivity extends Activity {

    private static final String SCRIPT_PATH = "/data/local/tmp/ptr_inject.sh";

    private TextView tvRootStatus, tvScriptStatus, tvLog;
    private EditText etPackage;
    private Button btnStart;
    private SharedPreferences prefs;
    private boolean hasRoot = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_panel);

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        tvRootStatus = findViewById(R.id.tvRootStatus);
        tvScriptStatus = findViewById(R.id.tvScriptStatus);
        tvLog = findViewById(R.id.tvLog);
        etPackage = findViewById(R.id.etPackage);
        btnStart = findViewById(R.id.btnStart);

        // Restaurar ultimo pacote usado
        String lastPkg = prefs.getString("last_package", "");
        if (!lastPkg.isEmpty()) {
            etPackage.setText(lastPkg);
        }

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFullInjection();
            }
        });

        checkRoot();
        checkScript();
    }

    private void appendLog(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvLog.append("\n" + msg);
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                hasRoot = RootHelper.isRooted();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (hasRoot) {
                            tvRootStatus.setText("Root Ativo");
                            tvRootStatus.setTextColor(0xFF00FF41);
                            appendLog("[+] Root detectado");
                        } else {
                            tvRootStatus.setText("Sem Root");
                            tvRootStatus.setTextColor(0xFFFF4444);
                            appendLog("[!] Root nao encontrado - app requer root");
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
        } else {
            tvScriptStatus.setText("Sera baixado ao iniciar");
            tvScriptStatus.setTextColor(0xFFFFAA00);
        }
    }

    private void startFullInjection() {
        final String pkg = etPackage.getText().toString().trim();
        if (pkg.isEmpty()) {
            appendLog("[!] Digite o pacote do jogo");
            return;
        }

        if (!pkg.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")) {
            appendLog("[!] Nome de pacote invalido");
            return;
        }

        if (!hasRoot) {
            appendLog("[!] Root necessario");
            return;
        }

        // Salvar pacote para proxima vez
        prefs.edit().putString("last_package", pkg).apply();

        setButtonState(false, "BAIXANDO...");
        tvLog.setText("[*] Iniciando processo...");

        final String key = prefs.getString("validated_key", "");
        final String serverUrl = "https://jawmodsuser.squareweb.app";

        new Thread(new Runnable() {
            @Override
            public void run() {
                // --- ETAPA 1: Download do script ---
                appendLog("[*] Baixando script do servidor...");
                String tempPath = getCacheDir().getAbsolutePath() + "/ptr_inject.sh";
                String dlResult = NetworkHelper.downloadScript(serverUrl, key, tempPath);

                if (!"OK".equals(dlResult)) {
                    appendLog("[!] Falha no download: " + dlResult);
                    setButtonState(true, "INICIAR");
                    return;
                }
                appendLog("[+] Download concluido");

                // --- ETAPA 2: Mover para /data/local/tmp/ com root ---
                setButtonState(false, "PREPARANDO...");
                appendLog("[*] Instalando script em " + SCRIPT_PATH + "...");

                String cpResult = RootHelper.executeAsRoot(
                        "cp " + tempPath + " " + SCRIPT_PATH +
                        " && chmod 755 " + SCRIPT_PATH +
                        " && chown root:root " + SCRIPT_PATH
                );

                if (cpResult.contains("Erro")) {
                    appendLog("[!] Falha ao instalar script: " + cpResult);
                    setButtonState(true, "INICIAR");
                    return;
                }
                appendLog("[+] Script instalado");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        checkScript();
                    }
                });

                // --- ETAPA 3: Executar script (ele mesmo abre o jogo) ---
                setButtonState(false, "EXECUTANDO...");
                appendLog("[*] Executando injecao para " + pkg + "...");
                appendLog("─────────────────────────────");

                String output = RootHelper.executeAsRoot("sh " + SCRIPT_PATH + " " + pkg);

                // Mostrar saida do script linha por linha
                if (output != null && !output.trim().isEmpty()) {
                    String[] lines = output.split("\n");
                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            appendLog(line);
                        }
                    }
                }

                appendLog("─────────────────────────────");
                appendLog("[+] Processo finalizado");
                setButtonState(true, "INICIAR");
            }
        }).start();
    }
}
