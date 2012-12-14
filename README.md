# Typesafe SNAP

This project aims to be the snappiest snaptastic snapster you've every snapping laid eyes on!

# How to build

This project uses [SBT 0.12](http://scala-sbt.org).   Make sure you have an SBT launcher, and run it in the checked out directory.


## Running the UI

    sbt> project snap-ui
    sbt> run

or just

    sbt "snap-ui/run"

## Staging a distribution

    sbt> snap-dist/stage

or just

    sbt> stage 

*Note: just stage will also run `ui/stage`*

Generates a distribution in the `dist/target/stage` directory.

## Building the Distribution

    sbt> snap-dist/dist

or just

    sbt> dist

*Note: just stage will also run `ui/dist`*

Generates the file `dist/target/universal/snap.zip`.

# Licensing?
