package com.android.support;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private EditText etKey;
    private Button btnValidate;
    private TextView tvStatus;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        // If key already validated, go straight to panel
        String savedKey = prefs.getString("validated_key", null);
        if (savedKey != null) {
            goToPanel();
            return;
        }

        setContentView(R.layout.activity_main);

        etKey = findViewById(R.id.etKey);
        btnValidate = findViewById(R.id.btnValidate);
        tvStatus = findViewById(R.id.tvStatus);

        btnValidate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateKey();
            }
        });
    }

    private void validateKey() {
        final String key = etKey.getText().toString().trim();
        if (key.isEmpty()) {
            tvStatus.setText("Digite uma key valida");
            tvStatus.setTextColor(0xFFFF4444);
            return;
        }

        btnValidate.setEnabled(false);
        tvStatus.setText("Validando...");
        tvStatus.setTextColor(0xFFAAAA00);

        final String serverUrl = "https://jawmodsuser.squareweb.app";

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String result = NetworkHelper.validateKey(serverUrl, key);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        btnValidate.setEnabled(true);
                        if ("OK".equals(result)) {
                            tvStatus.setText("Key valida!");
                            tvStatus.setTextColor(0xFF00FF41);
                            prefs.edit().putString("validated_key", key).apply();
                            Toast.makeText(MainActivity.this,
                                    "Acesso liberado!", Toast.LENGTH_SHORT).show();
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    goToPanel();
                                }
                            }, 1000);
                        } else {
                            tvStatus.setText(result);
                            tvStatus.setTextColor(0xFFFF4444);
                        }
                    }
                });
            }
        }).start();
    }

    private void goToPanel() {
        Intent intent = new Intent(this, PanelActivity.class);
        startActivity(intent);
        finish();
    }
}
