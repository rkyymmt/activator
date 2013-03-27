name := "hello-scala"

version := "1.0"

scalaVersion := "2.10.1"

scalaSource in Compile <<= baseDirectory / "app"

javaSource in Compile <<= baseDirectory / "app"

sourceDirectory in Compile <<= baseDirectory / "app"

scalaSource in Test <<= baseDirectory / "test"

javaSource in Test <<= baseDirectory / "test"

sourceDirectory in Test <<= baseDirectory / "test"

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test"

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
