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
        Project("root", file("."), settings = rootSettings) aggregate(protocol, remoteProbe, parent)

    lazy val rootSettings = Project.defaultSettings ++ unpublished

    lazy val sharedSettings = ScalariformPlugin.scalariformSettings ++ Seq(
        ScalariformKeys.preferences in Compile := formatPrefs,
        ScalariformKeys.preferences in Test    := formatPrefs) ++ Seq(
            scalacOptions := Seq("-unchecked", "-deprecation"),
            libraryDependencies += "com.novocode" % "junit-interface" % "0.7" % "test"
        )

    lazy val remoteProbe = Project(id = "sbt-child-remote-probe",
                               base = file("remote-probe"),
                               settings = remoteProbeSettings) dependsOn(protocol)

    lazy val remoteProbeSettings = Project.defaultSettings ++
        sharedSettings ++
        Seq(organization := "com.typesafe.sbtchild",
            name := "sbt-child-remote-probe",
            version := "0.1.0-SNAPSHOT",
            libraryDependencies <++= sbtVersion {
		(version) =>
		    Seq("org.scala-sbt" % "main" % version % "provided",
                        "org.scala-sbt" % "sbt" % version % "provided",
                        "org.scala-sbt" % "io" % version % "provided",
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
            version := "0.1.0-SNAPSHOT")

    lazy val parent = Project(id = "sbt-child-parent",
                              base = file("parent"),
                              settings = parentSettings) dependsOn(protocol)

    lazy val parentSettings = Project.defaultSettings ++
        sharedSettings ++
        Seq(organization := "com.typesafe.sbtchild",
            name := "sbt-child-parent",
            version := "0.1.0-SNAPSHOT",
            libraryDependencies <++= sbtVersion {
		(version) =>
		    Seq("com.typesafe.akka" % "akka-actor" % "2.0.3")
            })
}
