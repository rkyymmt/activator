import sbt._
import Keys._

// Defines how to generate properties file based on build attributes.
object Properties {

  val makePropertiesSource = TaskKey[Seq[File]]("make-properties-source")

  def writeIfChanged(file: java.io.File, content: String): Unit = {
    val oldContent = if (file.exists) IO.read(file) else ""
    if (oldContent != content) {
      IO.write(file, content)
    }
  }

  def makePropertyClassSetting(sbtDefaultVersion: String, scalaVersion: String): Seq[Setting[_]] = Seq(
    resourceGenerators in Compile <+= makePropertiesSource,
    makePropertiesSource <<= (version, resourceManaged in Compile, compile in Compile) map { (v, dir, analysis) =>
      val parent= dir / "activator" / "properties"
      IO createDirectory parent
      val target = parent / "activator.properties"

      writeIfChanged(target, makeJavaPropertiesString(v, sbtDefaultVersion, scalaVersion))

      Seq(target)
    }
  )

  
  def lastCompilationTime(analysis: sbt.inc.Analysis): Long = {
    val times = analysis.apis.internal.values map (_.compilation.startTime)
    if(times.isEmpty) 0L else times.max
  }
  

  def makeJavaPropertiesString(version: String, sbtDefaultVersion: String, scalaVersion: String): String =
    """|app.version=%s
       |sbt.default.version=%s
       |app.scala.version=%s
       |sbt.Xmx=512M
       |sbt.PermSize=128M
       |""".stripMargin format (version, sbtDefaultVersion, scalaVersion)
  
}
