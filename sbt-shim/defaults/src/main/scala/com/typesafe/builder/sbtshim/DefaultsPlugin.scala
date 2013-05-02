package com.typesafe.builder.sbtshim

import sbt._
import Keys._
object DefaultsPlugin extends Plugin {

  private val defaultsShimInstalled = SettingKey[Boolean]("defaults-shim-installed")

  val ACTIVATOR_LOCAL_RESOLVER_NAME = "activator-local"

  def useActivatorLocalRepo: Seq[Setting[_]] =
    Seq(
      fullResolvers <<= (fullResolvers, bootResolvers) map {
        case (rs, Some(boot)) if !(rs exists (_.name == ACTIVATOR_LOCAL_RESOLVER_NAME)) =>
          // Add just builder-local repo (as first checked)
          val localRepos = boot filter (_.name == ACTIVATOR_LOCAL_RESOLVER_NAME)
          localRepos ++ rs
        case (rs, _) => rs
      })

  // As a shim, fix the builder local repository to be used first on every project.
  override val settings = useActivatorLocalRepo ++ Seq(defaultsShimInstalled := true)
}
