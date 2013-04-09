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

  // As a shim, fix the builder local repository to be used first on every project. 
  override val settings = useBuilderLocalRepo
}