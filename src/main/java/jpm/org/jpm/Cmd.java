package org.jpm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Cmd {
    public static String run(String cmd) {
        var processBuilder = new ProcessBuilder();
        processBuilder.command("sh", "-c", cmd);
        try {
            var process = processBuilder.start();
            var output = new StringBuilder();
            var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }
            int exitValue = process.waitFor();
            if (exitValue == 0) {
                return output.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }
}
