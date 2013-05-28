import sbt._
import ActivatorBuild._
import Keys._


object LocalTemplateRepo {
  // TODO - We can probably move this to its own project, to more clearly delineate that the UI uses these
  // for local testing....
  val localTemplateSourceDirectory = SettingKey[File]("local-template-source-directory")
  val localTemplateCache = SettingKey[File]("local-template-cache")
  val localTemplateCacheCreated = TaskKey[File]("local-template-cache-created")
  
  
  def settings: Seq[Setting[_]] = Seq(
    localTemplateCache <<= target(_ / "template-cache"),
    localTemplateCacheCreated <<= (localTemplateCache, Keys.fullClasspath in Runtime) map makeTemplateCache,
    scalaVersion := Dependencies.scalaVersion,
    libraryDependencies += Dependencies.templateCache
  )
  
  def invokeTemplateCacheRepoMakerMain(cl: ClassLoader, arg: File): Unit =
    invokeMainFor(cl, "activator.templates.TemplateCacheSeedGenerator", Array(arg.getAbsolutePath))
  
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

  def makeTemplateCache(targetDir: File, classpath: Keys.Classpath): File = {
    // TODO - We should check for staleness here...
    if(!targetDir.exists) {
      IO createDirectory targetDir
      val cl = makeClassLoaderFor(classpath)
            invokeTemplateCacheRepoMakerMain(cl, targetDir)
    }
    targetDir
  }
}
