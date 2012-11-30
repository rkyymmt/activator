import sbt._

import Keys._
import Project.Initialize
import com.typesafe.sbtscalariform.ScalariformPlugin
import com.typesafe.sbtscalariform.ScalariformPlugin.ScalariformKeys

object SbtChildBuild extends Build {
    def formatPrefs = {
        import scalariform.formatter.preferences._
        FormattingPreferences()
           .setPreference(IndentSpaces, 2)
    }

    val unpublished = Seq(
        // no artifacts in this project
        publishArtifact := false,
        // make-pom has a more specific publishArtifact setting already
        // so needs specific override
        publishArtifact in makePom := false,
        // can't seem to get rid of ivy files except by no-op'ing the entire publish task
        publish := {},
        publishLocal := {}
    )

    lazy val root =
        Project("root", file("."), settings = rootSettings) aggregate(protocol, plugin)

    lazy val rootSettings = Project.defaultSettings ++ unpublished

    lazy val sharedSettings = ScalariformPlugin.scalariformSettings ++ Seq(
        ScalariformKeys.preferences in Compile := formatPrefs,
        ScalariformKeys.preferences in Test    := formatPrefs) ++ Seq(
            scalacOptions := Seq("-unchecked", "-deprecation")
        )

    lazy val plugin = Project(id = "sbt-child",
                               base = file("plugin"),
                               settings = pluginSettings)

    lazy val pluginSettings = Project.defaultSettings ++
        sharedSettings ++
        Seq(sbtPlugin := true,
            organization := "com.typesafe.sbt",
            name := "sbt-child",
            version := "0.1.0-SNAPSHOT",
            libraryDependencies <++= sbtVersion {
		(version) =>
		    Seq("org.scala-sbt" % "io" % version % "provided",
			"org.scala-sbt" % "logging" % version % "provided",
			"org.scala-sbt" % "process" % version % "provided")
            })

    lazy val protocol = Project(id = "sbt-child-protocol",
                               base = file("protocol"),
                               settings = protocolSettings)

    lazy val protocolSettings = Project.defaultSettings ++
        sharedSettings ++
        Seq(organization := "com.typesafe.sbtchild",
            name := "sbt-child-protocol",
            version := "0.1.0-SNAPSHOT",
            libraryDependencies <++= sbtVersion {
		(version) =>
		    Seq("org.scala-sbt" % "io" % version,
			"org.scala-sbt" % "logging" % version,
			"org.scala-sbt" % "process" % version)
            })
}
