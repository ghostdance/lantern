#!/usr/bin/env bash

function die() {
  echo $*
  exit 1
}

if [ $# -ne "2" ]
then
    die "$0: Received $# args... version and whether or not this is a release required"
fi
#RELEASE=$2

./debInstall.bash $* 64 
