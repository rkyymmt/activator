package snap
package tests

/** Base class for integration tests. */
abstract class IntegrationTest extends DelayedInit with xsbti.AppMain {
  // Junk to make delayed init work.
  private var _config: xsbti.AppConfiguration = null
  private var _test: () => Unit = null
  final def delayedInit(x: => Unit): Unit = _test = () => x

  /** Returns the current sbt launcher configuration for the test. */
  final def configuration: xsbti.AppConfiguration = _config

  // Runs our test, we hardcode this to return success in the absence of failure, so we can use
  // classic exceptions to fail an integration test.
  final def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
    try withContextClassloader {
      _config = configuration
      _test()
      // IF we don't throw an exception, we've succeeded
      Success
    } catch {
      case t: Exception =>
        t.printStackTrace()
        Failure
    }

  private def cleanUriFileString(file: String): String =
    file.replaceAll(" ", "%20")

  /** Return a process builder that will run SNAP in a directory with the given args. */
  final def run_activator(args: Seq[String], cwd: java.io.File): sys.process.ProcessBuilder = {
    // TODO - pass on all props...
    val fullArgs = Seq(
      "java",
      "-Dsbt.boot.directory=" + sys.props("sbt.boot.directory"),
      "-Dactivator.home=" + cleanUriFileString(sys.props("activator.home")),
      "-jar",
      activator.properties.ActivatorProperties.ACTIVATOR_LAUNCHER_JAR) ++ args
    sys.process.Process(fullArgs, cwd)
  }
}
