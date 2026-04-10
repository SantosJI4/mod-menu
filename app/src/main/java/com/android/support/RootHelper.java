package com.android.support;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class RootHelper {

    public interface LineCallback {
        void onLine(String line);
    }

    public static boolean isRooted() {
        try {
            Process process = Runtime.getRuntime().exec("su -c id");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            process.waitFor();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    public static String executeAsRoot(String command) {
        return executeAsRoot(command, null);
    }

    public static String executeAsRoot(String command, LineCallback callback) {
        try {
            // Executa com su -c direto, sem pipe de stdin
            // Igual a rodar no terminal: su -c "cd /dir && ./comando"
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            BufferedReader errReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                if (callback != null) {
                    callback.onLine(line);
                }
            }
            while ((line = errReader.readLine()) != null) {
                String errLine = "[ERR] " + line;
                output.append(errLine).append("\n");
                if (callback != null) {
                    callback.onLine(errLine);
                }
            }

            reader.close();
            errReader.close();
            int exitCode = process.waitFor();
            
            String exitLine = "[EXIT] codigo: " + exitCode;
            output.append(exitLine).append("\n");
            if (callback != null) {
                callback.onLine(exitLine);
            }

            return output.toString();
        } catch (Exception e) {
            String err = "Erro: " + e.getMessage();
            if (callback != null) {
                callback.onLine(err);
            }
            return err;
        }
    }
}
