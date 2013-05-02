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
  
  def invokeTemplateCacheRepoMakerMain(classpath: Keys.Classpath, arg: File): Unit = {

    val jars = classpath map (_.data.toURL)
    val classLoader = new java.net.URLClassLoader(jars.toArray, null)
    val maker = classLoader.loadClass("snap.cache.generator.TemplateRepoIndexGenerator")
    val mainMethod = maker.getMethod("main", classOf[Array[String]])
    mainMethod.invoke(null, Array(arg.getAbsolutePath))
  }

  // Loads the id from a template metadata file.
  def loadId(metadata: File): Option[String] = {
    val props = new java.util.Properties
    IO.load(props, metadata)
    Option(props.getProperty("id"))
  } 
  
  def obtainLocalTemplates(sourceDir: File, targetDir: File): File = {
    // TODO - we should be loading the templates and cache from the typesafe.com server here, but for
	// now we're generating it locally.
    for {
      templateDir <- IO.listFiles(sourceDir) 
      metadata = templateDir / "activator.properties"
      if metadata.exists
      id <- loadId(metadata)
      // TODO - Figure out the true structure (do we have intermediate dirs)
      outDir = targetDir / id
    } IO.copyDirectory(templateDir, outDir)
    targetDir
  }

  def makeTemplateCache(sourceDir: File, targetDir: File, classpath: Keys.Classpath): File = {
	// TODO - We should check for staleness here...
    if(!targetDir.exists) {
	  IO createDirectory targetDir
      obtainLocalTemplates(sourceDir, targetDir)
	  invokeTemplateCacheRepoMakerMain(classpath, targetDir)
    }
    targetDir
  }
}