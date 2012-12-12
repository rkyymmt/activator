# Typesafe SNAP

This project aims to be the snappiest snaptastic snapster you've every snapping laid eyes on!

# How to build

This project uses [SBT 0.12](http://scala-sbt.org).   Make sure you have an SBT launcher, and run it in the checked out directory.


## Running the UI

    sbt> project snap-ui
    sbt> run

or just

    sbt "snap-ui/run"

## Building the Distribution

    sbt> snap-dist/univeral:package-bin`

Generates the file `dist/target/universal/snap.zip`.

*Note: I plan to make this just `dist` in the future*


# Licensing?
