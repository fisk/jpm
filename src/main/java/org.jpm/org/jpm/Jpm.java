package org.jpm;

import java.lang.module.ModuleFinder;
import java.util.Set;
import java.util.regex.Pattern;

public class Jpm {
    private String[] _args;
    private int _index = 1;
    public static void main(String[] args) throws Exception {
        var main = new Jpm(args);
        main.run();
    }

    public Jpm(String[] args) {
        _args = args;
    }

    private void run() {
        if (_index >= _args.length) {
            help();
            return;
        }
        switch (_args[_index++]) {
        case "--help":
            help();
            break;
        case "build":
            build();
            break;
        case "install":
            install();
            break;
        case "uninstall":
            uninstall();
            break;
        case "get":
            get();
            break;
        default:
            help();
            break;
        }
    }

    private void help() {
        System.out.println("Usage: jpm <command> <command-args>");
        System.out.println("Commands:");
        System.out.println("\tbuild - This command builds your project");
    }

    private void build() {
        new BuildCommand().run();
    }

    private void get() {
        String version = null;
        String module = _args[_index++];
        while (_index < _args.length) {
          switch (_args[_index++]) {
            case "--version":
              version = _args[_index++];
              break;
          }
        }
        new GetCommand(module, version).run();
    }

    private void install() {
        new InstallCommand().run();
    }

    private void uninstall() {
    }
}
