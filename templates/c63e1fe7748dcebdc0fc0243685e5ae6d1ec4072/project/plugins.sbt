// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// Use the Play sbt plugin for Play projects
addSbtPlugin("play" % "sbt-plugin" % "2.1.0")

// TODO - Just pull in boot repos
resolvers ++= {
  // If local repo exists, use it.
  val builderHome = Option(sys.props("builder.home"))
  val repo = for {
    home <- builderHome
    localRepoDir = (new File(home)) / "repository"
    if localRepoDir.exists
  } yield Resolver.file("builder-local", localRepoDir)(Resolver.ivyStylePatterns)
  repo.toSeq
}