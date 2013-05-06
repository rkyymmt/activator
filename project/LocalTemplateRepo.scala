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
    localTemplateSourceDirectory <<= (baseDirectory in ThisBuild) apply (_ / "templates"),
    localTemplateCache <<= target(_ / "template-cache"),
    localTemplateCacheCreated <<= (localTemplateSourceDirectory, localTemplateCache, 
        Keys.fullClasspath in Runtime in TheActivatorBuild.cache) map makeTemplateCache
  )
  
  def invokeTemplateCacheRepoMakerMain(cl: ClassLoader, arg: File): Unit =
    invokeMainFor(cl, "snap.cache.generator.TemplateRepoIndexGenerator", Array(arg.getAbsolutePath))
  
  
  // Loads the id from a template metadata file.
  def loadId(repoDir: File, cl: ClassLoader): Option[String] = {
    try {
      val obj = cl.loadClass("snap.cache.generator.IdGenerator")
      val maker = obj.getMethod("generateId", classOf[File])
      val result = maker.invoke(null, repoDir)
      Some(result.toString)
    } catch {
      case e: Exception => 
        e.printStackTrace()
        None
    }
  } 
  
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

  
  def obtainLocalTemplates(sourceDir: File, targetDir: File, cl: ClassLoader): File = {
    // TODO - we should be loading the templates and cache from the typesafe.com server here, but for
	// now we're generating it locally.
    for {
      templateDir <- IO.listFiles(sourceDir) 
      id <- loadId(templateDir, cl)
      // TODO - Figure out the true structure (do we have intermediate dirs)
      outDir = targetDir / id
    } IO.copyDirectory(templateDir, outDir)
    targetDir
  }

  def makeTemplateCache(sourceDir: File, targetDir: File, classpath: Keys.Classpath): File = {
	// TODO - We should check for staleness here...
    if(!targetDir.exists) {
	  IO createDirectory targetDir
	  val cl = makeClassLoaderFor(classpath)
      obtainLocalTemplates(sourceDir, targetDir, cl)
	  invokeTemplateCacheRepoMakerMain(cl, targetDir)
    }
    targetDir
  }
}