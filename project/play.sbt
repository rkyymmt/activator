resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// TODO - This ties us to a particular play version... would be ideal to expose the version somewhere else...
addSbtPlugin("play" % "sbt-plugin" % "2.1-RC1")
