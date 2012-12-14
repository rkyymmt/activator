import sbt._
import Keys._

// Defines how to generate properties file based on build attributes.
object Properties {

  val makePropertiesSource = TaskKey[Seq[File]]("make-properties-source")

  def makePropertyClassSetting(sbtVersion: String): Seq[Setting[_]] = Seq(
    resourceGenerators in Compile <+= makePropertiesSource,
    makePropertiesSource <<= (version, resourceManaged in Compile, compile in Compile) map { (v, dir, analysis) =>
      import java.util.{Date, TimeZone}
	  val formatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")
	  formatter setTimeZone TimeZone.getTimeZone("GMT")
	  val timestamp = formatter.format(new Date)
      val parent= dir / "snap" / "properties"
      IO createDirectory parent
      val target = parent / "snap.properties"
      if(!target.exists || target.lastModified < lastCompilationTime(analysis)) {
        IO.write(target, makeJavaPropertiesString(v, sbtVersion))
      }
      Seq(target)
    }
  )

  
  def lastCompilationTime(analysis: sbt.inc.Analysis): Long = {
    val times = analysis.apis.internal.values map (_.compilation.startTime)
    if(times.isEmpty) 0L else times.max
  }
  

  def makeJavaPropertiesString(version: String, sbtVersion: String): String =
    """|app.version=%s
       |sbt.version=$s
       |""".stripMargin format (version, sbtVersion)
  
  
}