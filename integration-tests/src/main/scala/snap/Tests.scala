package snap

// Helper methods for running tests.
object tests {
  
  // This method has to be used around any code the makes use of Akka to ensure the classloader is right.
  def withContextClassloader[A](f: => A): A = {
    val current = Thread.currentThread
    val old = current.getContextClassLoader
    current setContextClassLoader getClass.getClassLoader
    try f
    finally current setContextClassLoader old
  }
  
  // Success and failure conditions for tests.  
  case object Success extends xsbti.Exit {
    val code = 0
  }
  case object Failure extends xsbti.Exit {
    val code = 1
  }
  
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
  }
}


