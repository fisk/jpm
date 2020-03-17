#!/bin/sh

rm -rf build
mkdir -p build
javac -d build --module-path lib --module-source-path src/main/java src/main/java/**/*.java --module jpm -source 11
java --module-path="lib:build/jpm" -m jpm/org.jpm.Jpm bootstrap build
