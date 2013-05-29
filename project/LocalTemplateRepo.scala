import sbt._
import ActivatorBuild._
import Keys._


object LocalTemplateRepo {
  // TODO - We can probably move this to its own project, to more clearly delineate that the UI uses these
  // for local testing....
  val localTemplateSourceDirectory = SettingKey[File]("local-template-source-directory")
  val localTemplateCache = SettingKey[File]("local-template-cache")
  val localTemplateCacheCreated = TaskKey[File]("local-template-cache-created")
  val remoteTemplateCacheUri = SettingKey[String]("remote-template-cache-uri")
  
  
  def settings: Seq[Setting[_]] = Seq(
    localTemplateCache <<= target(_ / "template-cache"),
    localTemplateCacheCreated <<= (localTemplateCache, Keys.fullClasspath in Runtime, remoteTemplateCacheUri) map makeTemplateCache,
    scalaVersion := Dependencies.scalaVersion,
    libraryDependencies += Dependencies.templateCache,
    // TODO - modify for release.
    remoteTemplateCacheUri := "http://downloads.typesafe.com/typesafe-activator-test"
  )
  
  def invokeTemplateCacheRepoMakerMain(cl: ClassLoader, dir: File, uri: String): Unit =
    invokeMainFor(cl, "activator.templates.TemplateCacheSeedGenerator", Array("-remote", uri, dir.getAbsolutePath))
  
  private def makeClassLoaderFor(classpath: Keys.Classpath): java.net.URLClassLoader = {
    val jars = classpath map (_.data.toURL)
    new java.net.URLClassLoader(jars.toArray, null)
  }
  
  private def invokeMainFor(cl: ClassLoader, mainClass: String, args: Array[String]): Unit = {
    println("Loading " + mainClass + " from: " + cl)
    val maker = cl.loadClass(mainClass)
    println("Invoking object: " + maker)
    val mainMethod = maker.getMethod("main", classOf[Array[String]])
    println("Invoking maker: " + maker)
    mainMethod.invoke(null, args)
  }

  def makeTemplateCache(targetDir: File, classpath: Keys.Classpath, uri: String): File = {
    // TODO - We should check for staleness here...
    if(!targetDir.exists) try {
      IO createDirectory targetDir
      val cl = makeClassLoaderFor(classpath)
      // Akka requires this crazy
      val old = Thread.currentThread.getContextClassLoader
      Thread.currentThread.setContextClassLoader(cl)
      try invokeTemplateCacheRepoMakerMain(cl, targetDir, uri)
      finally Thread.currentThread.setContextClassLoader(old)
    } catch {
      case ex: Exception =>
         IO delete targetDir
         throw ex
    }
    targetDir
  }
}
