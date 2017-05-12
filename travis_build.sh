#!/usr/bin/env bash
cd fk-prof/recorder
     cmake CMakeLists.txt
     make
cd -
mvn install -DskipTests=true -Dmaven.javadoc.skip=true -B -V
mvn test -B