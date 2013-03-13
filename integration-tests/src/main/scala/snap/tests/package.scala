package snap

import builder.properties.BuilderProperties._

// Helper methods for running tests.
package object tests {

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

  def createFile(name: java.io.File, content: String): Unit = {
    val writer = new java.io.FileWriter(name)
    try writer.write(content)
    finally writer.close()
  }

  /** Creates a dummy project we can run Builder against. */
  def makeDummySbtProject(dir: java.io.File): java.io.File = {
    snap.cache.IO.createDirectory(dir)
    val project = new java.io.File(dir, "project")
    snap.cache.IO.createDirectory(project)
    val props = new java.io.File(project, "build.properties")
    createFile(props, "sbt.version=" + SBT_VERSION)
    val scalaSource = new java.io.File(dir, "src/main/scala")
    snap.cache.IO.createDirectory(scalaSource)
    val main = new java.io.File(scalaSource, "hello.scala")
    createFile(main, "object Main extends App { println(\"Hello World\") }\n")
    dir
  }

  /** waits for a simple "GET" command */
  def waitForHttpServerStartup(uri: String): Boolean = {
    import java.net._
    def isAlive: Boolean = try {
      HttpURLConnection setFollowRedirects true
      val url = new URL(uri)
      val request = url.openConnection()
      request setDoOutput true
      val http = request.asInstanceOf[HttpURLConnection]
      http setRequestMethod "GET"
      try {
        http.connect()
        val response = http.getResponseCode
        response == HttpURLConnection.HTTP_OK
      } finally http.disconnect()
    } catch {
      // Any exceptions are bad news here.
      case t: Throwable => false
    }
    // Keep sleeping until we see the server respond.
    // Default to waiting 30 seconds.
    def checkAlive(remaining: Int = 60): Boolean =
      remaining match {
        case 0 => isAlive
        case n =>
          if (isAlive) true
          else {
            Thread.sleep(1000L)
            checkAlive(remaining - 1)
          }
      }
    checkAlive()
  }
}

