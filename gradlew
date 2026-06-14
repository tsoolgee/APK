#!/bin/sh
#
# Gradle start up script for UN*X
#
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
APP_HOME="`dirname \"$0\"`"
APP_HOME="`( cd \"$APP_HOME\" && pwd )`"

# Classpath
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# JVM options
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# OS specific
cygwin=false
msys=false
darwin=false
case "`uname`" in
  CYGWIN* ) cygwin=true ;;
  Darwin* ) darwin=true ;;
  MSYS* | MINGW* ) msys=true ;;
esac

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
