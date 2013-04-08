package com.typesafe.builder.sbtshim

import sbt._
import Keys._
object DefaultsPlugin extends Plugin {

  val BUILDER_LOCAL_RESOLVER_NAME = "builder-local"

  def useBuilderLocalRepo: Seq[Setting[_]] =
    Seq(
      fullResolvers <<= (fullResolvers, bootResolvers) map {
        case (rs, Some(boot)) if !(rs exists (_.name == BUILDER_LOCAL_RESOLVER_NAME)) =>
          // Add just builder-local repo (as first checked)
          val localRepos = boot filter (_.name == BUILDER_LOCAL_RESOLVER_NAME)
          localRepos ++ rs
        case (rs, _) => rs
      })

  def useBuilderSourceLayout: Seq[Setting[_]] =
    Seq(scalaSource in Compile <<= baseDirectory / "app",
      javaSource in Compile <<= baseDirectory / "app",
      sourceDirectory in Compile <<= baseDirectory / "app",
      scalaSource in Test <<= baseDirectory / "test",
      javaSource in Test <<= baseDirectory / "test",
      sourceDirectory in Test <<= baseDirectory / "test",
      resourceDirectory in Compile <<= baseDirectory / "conf")

  def builderDefaults: Seq[Setting[_]] =
    useBuilderLocalRepo ++ useBuilderSourceLayout ++ Seq(
      scalaVersion := "2.10.1")
}