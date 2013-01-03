import sbt._
import Keys._

// Defines how to generate properties file based on build attributes.
object Properties {

  val makePropertiesSource = TaskKey[Seq[File]]("make-properties-source")

  def makePropertyClassSetting(sbtVersion: String, scalaVersion: String): Seq[Setting[_]] = Seq(
    resourceGenerators in Compile <+= makePropertiesSource,
    makePropertiesSource <<= (version, resourceManaged in Compile, compile in Compile) map { (v, dir, analysis) =>
      val parent= dir / "snap" / "properties"
      IO createDirectory parent
      val target = parent / "snap.properties"
      if(!target.exists || target.lastModified < lastCompilationTime(analysis)) {
        IO.write(target, makeJavaPropertiesString(v, sbtVersion, scalaVersion))
      }
      Seq(target)
    }
  )

  
  def lastCompilationTime(analysis: sbt.inc.Analysis): Long = {
    val times = analysis.apis.internal.values map (_.compilation.startTime)
    if(times.isEmpty) 0L else times.max
  }
  

  def makeJavaPropertiesString(version: String, sbtVersion: String, scalaVersion: String): String =
    """|app.version=%s
       |sbt.version=%s
       |sbt.scala.version=%s
       |app.scala.version=%s
       |""".stripMargin format (version, sbtVersion, sbtScalaVersion(sbtVersion), scalaVersion)
  
  
  def sbtScalaVersion(sbtVersion: String): String =
    (sbtVersion split "[\\.\\-]" take 3) match {
      case Array("0", "12", _) => "2.9.2"
      case Array("0", "13", _) => "2.10.0"
      case _                   => "2.9.1"
    }
  
}
