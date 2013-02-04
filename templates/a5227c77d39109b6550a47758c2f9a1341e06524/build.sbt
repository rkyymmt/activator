name := "hello-scala"

version := "1.0"

scalaVersion := "2.10.0"

scalaSource in Compile <<= baseDirectory / "app"

javaSource in Compile <<= baseDirectory / "app"

sourceDirectory in Compile <<= baseDirectory / "app"
