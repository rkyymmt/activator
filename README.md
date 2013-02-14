# Typesafe SNAP

This project aims to be the snappiest snaptastic snapster you've every snapping laid eyes on!

# How to build

This project uses [SBT 0.12](http://scala-sbt.org).   Make sure you have an SBT launcher, and run it in the checked out directory.


## Running the UI

    sbt> project snap-ui
    sbt> run

or just

    sbt "snap-ui/run"


## Testing

There are two types of tests:  Unit tests and integration tests.

### Unit Tests

To run unit tests, simply:

    sbt> test

To run the tests of a particular project, simply:

    sbt> <project>/test

To run a specific test, simply:

    sbt> test-only TestName

## Integration Tests

To run all the integration tests, simply:

    sbt> integration-tests



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

## Publishing the Distribution

First, make sure your credentials are in an appropriate spot.  For me, that's in `~/.sbt/user.sbt` with the following content:

    credentials += Credentials("Amazon S3", "downloads.typesafe.com.s3.amazonaws.com", <AWS KEY>, <AWS PW>)

Then you can run simply:

    sbt> snap-dist/s3-upload

*OR*

    sbt> s3-upload
    


# Issues

If you run into staleness issues with a staged release of SNAP, just run `reload` in SBT to regenerate the version number and then run `stage` again.   This should give you a new stable version of SNAP for the sbt-launcher so that the new code is used.   Should only be needed when doing integration tests.

# Licensing?
