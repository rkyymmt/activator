package com.typesafe.sbtchild

import sbt._
import builder.properties.BuilderProperties._

object PlaySupport {
  def run(state: State, client: ipc.Client, replyTo: Long, serial: Long): State = {
    // TODO -Use our play plugin...
    System.err.println("HOLY S**** IT'S PLAY!")
    // TODO - Should we install the shim here if needed?
    SetupSbtChild.runInputTask(InputKey[Unit]("play-shim-run"), state, "")
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
    extracted.getOpt(SettingKey[Boolean]("play-shim-installed")) getOrElse false
  }

  def doShim(state: State): Unit = {
    val extracted = Project.extract(state)
    val bd = extracted get Keys.baseDirectory
    // TODO - look this up...
    val shimPluginFile = bd / "project" / "builder-play-shim.sbt"
    // TODO - Don't hardcode group and name and stuff...
    IO.write(shimPluginFile, """addSbtPlugin("com.typesafe.builder" % "sbt-shim-play" % """" + APP_VERSION + "\")")
  }

  def ensureShim(state: State): Unit = {
    if (isPlayProject(state) && !isPlayShimInstalled(state)) {
      doShim(state)
      System.err.println("Installed Play Support, need to reboot SBT.")
      // By Erroring out (and doing so before responding to protocol method),
      // We force the Sbt process to reload and try again...
      sys.error("Installed Play Support, need to reboot SBT.")
    }
  }

}
