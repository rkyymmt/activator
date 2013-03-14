package com.typesafe.sbtchild

import sbt._

object PlaySupport {
  def run(state: State, client: ipc.Client, replyTo: Long, serial: Long): State = {
    // TODO -Use our play plugin...
    System.err.println("HOLY S**** IT'S PLAY!")
    // TODO - Should we install the shim here if needed?

    state
  }

  // TODO - Specify project too...
  def isPlayProject(state: State): Boolean = {
    val extracted = Project.extract(state)
    extracted.getOpt(SettingKey[Boolean]("play-plugin")).isDefined
  }

    // TODO - Specify project too...
  def isPlayShimInstalled(state: State): Boolean = {
    val extracted = Project.extract(state)
    extracted.getOpt(SettingKey[Boolean]("play-shim-plugin")).isDefined
  }

}