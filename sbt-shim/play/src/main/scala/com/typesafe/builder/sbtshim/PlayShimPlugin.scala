package com.typesafe.builder.sbtshim

import sbt._
import Keys._
import play.Project._
import sbt.InputTask
import sbt.InputTask

object PlayShimKeys {
  val playShimInstalled = SettingKey[Boolean]("play-shim-installed")

  val playShimRun = InputKey[Unit]("play-shim-run")
}

object PlayShimPlugin extends Plugin {
  import PlayShimKeys._

  override val settings: Seq[Setting[_]] = Seq(
    playShimInstalled := true,
    playShimRun <<= inputTask { (args: TaskKey[Seq[String]]) =>
      (args, state) map run
    })

  def run(args: Seq[String], state: State): Unit = {
    System.err.println("HOLY SH***, I'm COMPILING PLAY")
  }
}
