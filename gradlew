#!/bin/sh
APP_HOME="\$(dirname \"\\")"
APP_HOME="\$( cd \"\\" && pwd )"
CLASSPATH=\/gradle/wrapper/gradle-wrapper.jar
if [ -n "\" ] ; then
    JAVACMD="\/bin/java"
else
    JAVACMD=java
fi
exec "\" -classpath "\" org.gradle.wrapper.GradleWrapperMain "\$@"
