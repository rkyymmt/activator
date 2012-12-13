import sbt._
import Keys._

// Defines how to generate properties file based on build attributes.
object Properties {

  val makePropertiesSource = TaskKey[Seq[File]]("make-properties-source")

  def makePropertyClassSetting(sbtVersion: String): Seq[Setting[_]] = Seq(
    sourceGenerators in Compile <+= makePropertiesSource,
    makePropertiesSource <<= (version, sourceManaged in Compile) map { (v, dir) =>
      val parent= dir / "snap" / "properties"
      IO createDirectory parent
      val target = parent / "SnapProperties.java"
      IO.write(target, makeJavaPropertiesString(v, sbtVersion))
      Seq(target)
    }
  )


  def makeJavaPropertiesString(version: String, sbtVersion: String): String =
    """|package snap.properties;
       |
       |public class SnapProperties {
       |   public static String APP_VERSION = "%s";
       |   public static String SBT_VERSION = "%s";
       |}
       |""".stripMargin format (version, sbtVersion)
}