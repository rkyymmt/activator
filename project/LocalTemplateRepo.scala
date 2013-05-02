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

  def makeTemplateCache(sourceDir: File, targetDir: File, classpath: Keys.Classpath): File = {
	// TODO - We should check for staleness here...
    if(!targetDir.exists) {
	  IO createDirectory targetDir
	  // TODO - we should be loading the templates and cache from the typesafe.com server here, but for
	  // now we're generating it locally.
	  IO.copyDirectory(sourceDir, targetDir)
	  invokeTemplateCacheRepoMakerMain(classpath, targetDir)
    }
    targetDir
  }
}