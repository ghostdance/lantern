#!/usr/bin/env bash
function die() {
  echo $*
  exit 1
}

mvn --version || die "Please install maven from http://maven.apache.org" 

#pushd ..
test -d target || ./installDeps.bash
mvn package -Dmaven.artifact.threads=1 -Dmaven.test.skip=true || die "Could not package"
