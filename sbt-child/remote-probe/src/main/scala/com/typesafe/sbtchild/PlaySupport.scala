package com.typesafe.sbtchild

import sbt._
import builder.properties.BuilderProperties._
import com.typesafe.sbtchild.protocol.TaskNames
import com.typesafe.sbt.ui.{ Context => UIContext, Params, RequestHandler }

/** This guy provides the support we need to run play projects... */
object PlaySupport {

  private val playRunHandler: RequestHandler = run _

  // Note: we import default shim for default behaviors and override with our own as necessary.
  import DefaultsShim._
  val findHandler: PartialFunction[String, RequestHandler] = {
    case TaskNames.name => nameHandler
    case TaskNames.discoveredMainClasses => discoveredMainClassesHandler
    case TaskNames.watchTransitiveSources => watchTransitiveSourcesHandler
    case TaskNames.compile => compileHandler
    case TaskNames.run => playRunHandler
    case TaskNames.runMain => runMainHandler
    case TaskNames.test => testHandler
  }

  // This is the shim'd run task we use instead of play's default.
  private val playRunShimTask = InputKey[Unit]("play-shim-run")

  def run(state: State, context: UIContext, params: Params): (State, Params) = {
    // TODO - Lookup default port and ensure it's ready/running....
    System.err.println("HOLY S**** IT'S PLAY!")
    val s = SbtUtil.runInputTask(playRunShimTask, state, args = "")
    (s, makeResponseParams(protocol.RunResponse(success = true,
      task = protocol.TaskNames.runMain)))
    (state, params)
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
