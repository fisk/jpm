package org.jpm;

public class CleanCommand {
    public void run() {
        var proj = new Project();
        var buildPath = proj.getBuildPath();
        if (buildPath != null && buildPath.toFile().exists()) {
            Cmd.run("rm -rf " + buildPath);
        }
    }
}
