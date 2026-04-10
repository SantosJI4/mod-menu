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

    private static final String SCRIPT_PATH = "/data/local/tmp/inject.sh";

    private TextView tvRootStatus, tvScriptStatus, tvLog;
    private EditText etPackage;
    private Button btnDownload, btnInject;
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
        btnDownload = findViewById(R.id.btnDownload);
        btnInject = findViewById(R.id.btnInject);

        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                downloadScript();
            }
        });

        btnInject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                injectAndLaunch();
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
                            appendLog("[!] Root nao encontrado");
                            btnInject.setEnabled(false);
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
            tvScriptStatus.setText("Nao baixado");
            tvScriptStatus.setTextColor(0xFFFF4444);
        }
    }

    private void downloadScript() {
        if (!hasRoot) {
            appendLog("[!] Root necessario para salvar em /data/local/tmp/");
            return;
        }

        btnDownload.setEnabled(false);
        appendLog("[*] Iniciando download do script...");

        final String key = prefs.getString("validated_key", "");
        final String serverUrl = prefs.getString("server_url", "http://SEU_IP:5000");

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Download to app cache first, then move with root
                final String tempPath = getCacheDir().getAbsolutePath() + "/inject.sh";
                final String result = NetworkHelper.downloadScript(serverUrl, key, tempPath);

                if ("OK".equals(result)) {
                    appendLog("[+] Download concluido");
                    // Move to /data/local/tmp/ using root
                    String mvResult = RootHelper.executeAsRoot(
                            "cp " + tempPath + " " + SCRIPT_PATH + " && chmod 755 " + SCRIPT_PATH
                    );
                    appendLog("[*] Movendo para " + SCRIPT_PATH);
                    if (mvResult.contains("Erro")) {
                        appendLog("[!] " + mvResult);
                    } else {
                        appendLog("[+] Script pronto em " + SCRIPT_PATH);
                    }
                } else {
                    appendLog("[!] Falha: " + result);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnDownload.setEnabled(true);
                        checkScript();
                    }
                });
            }
        }).start();
    }

    private void injectAndLaunch() {
        final String pkg = etPackage.getText().toString().trim();
        if (pkg.isEmpty()) {
            appendLog("[!] Digite o pacote do jogo");
            return;
        }

        // Validate package name to prevent command injection
        if (!pkg.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")) {
            appendLog("[!] Nome de pacote invalido");
            return;
        }

        File script = new File(SCRIPT_PATH);
        if (!script.exists()) {
            appendLog("[!] Script nao encontrado. Baixe primeiro.");
            return;
        }

        if (!hasRoot) {
            appendLog("[!] Root necessario para injetar");
            return;
        }

        btnInject.setEnabled(false);
        appendLog("[*] Iniciando " + pkg + "...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Launch the game
                RootHelper.executeAsRoot(
                        "monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1"
                );
                appendLog("[+] Jogo iniciado");

                // Wait for game process to start
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                }

                appendLog("[*] Executando script de injecao...");
                String injectResult = RootHelper.executeAsRoot(
                        "sh " + SCRIPT_PATH + " " + pkg
                );
                if (!injectResult.trim().isEmpty()) {
                    appendLog("[>] " + injectResult.trim());
                }
                appendLog("[+] Injecao concluida");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnInject.setEnabled(true);
                    }
                });
            }
        }).start();
    }
}
