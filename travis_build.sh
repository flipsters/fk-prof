#!/usr/bin/env bash
cd recoder
     cmake CMakeLists.txt
     make
cd -
mvn install -DskipTests=true -Dmaven.javadoc.skip=true -B -V
mvn test -B