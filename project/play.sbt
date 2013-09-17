resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// remove this once Play is 2.2.0 final
resolvers += Classpaths.typesafeSnapshots

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.0-RC2")
