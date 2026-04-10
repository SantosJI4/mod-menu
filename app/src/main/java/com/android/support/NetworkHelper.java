package com.android.support;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class NetworkHelper {

    public static String validateKey(String serverUrl, String key) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(serverUrl + "/api/validate");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            // Sanitize key: remove any quotes to prevent JSON injection
            String safeKey = key.replaceAll("[\"\\\\]", "");
            String json = "{\"key\":\"" + safeKey + "\"}";
            OutputStream os = conn.getOutputStream();
            os.write(json.getBytes("UTF-8"));
            os.close();

            int code = conn.getResponseCode();
            if (code == 200) {
                return "OK";
            } else {
                InputStream errStream = conn.getErrorStream();
                if (errStream != null) {
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(errStream));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();
                    String body = sb.toString();
                    // Parse "message":"value" from JSON
                    int idx = body.indexOf("\"message\"");
                    if (idx >= 0) {
                        int colon = body.indexOf(":", idx);
                        int qStart = body.indexOf("\"", colon + 1);
                        int qEnd = body.indexOf("\"", qStart + 1);
                        if (qStart >= 0 && qEnd > qStart) {
                            return body.substring(qStart + 1, qEnd);
                        }
                    }
                }
                return "Key invalida (HTTP " + code + ")";
            }
        } catch (Exception e) {
            return "Erro de conexao: " + e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static String downloadScript(String serverUrl, String key, String savePath) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(serverUrl + "/api/download/script");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Auth-Key", key);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            int code = conn.getResponseCode();
            if (code == 200) {
                InputStream is = conn.getInputStream();
                File file = new File(savePath);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(file);
                byte[] buffer = new byte[4096];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                is.close();
                return "OK";
            } else {
                return "Erro HTTP " + code;
            }
        } catch (Exception e) {
            return "Erro: " + e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
