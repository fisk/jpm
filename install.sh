#!/bin/sh

rm -rf build
mkdir -p build
javac -d build --module-path lib --module-source-path src/main/java src/main/java/**/*.java --module org.jpm -source 11
java --module-path="lib:build/org.jpm" -m org.jpm/org.jpm.Jpm bootstrap install
