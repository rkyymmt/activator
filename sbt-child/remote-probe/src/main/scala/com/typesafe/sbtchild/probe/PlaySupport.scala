package com.typesafe.sbtchild
package probe

import sbt._
import builder.properties.BuilderProperties._
import com.typesafe.sbtchild.protocol.TaskNames
import com.typesafe.sbt.ui.{ Context => UIContext, Params }
import com.typesafe.sbt.ui.{ Context => UIContext }
import com.typesafe.sbtchild.SbtUtil
import com.typesafe.sbtchild.probe.DefaultsShim.compileHandler
import com.typesafe.sbtchild.probe.DefaultsShim.discoveredMainClassesHandler
import com.typesafe.sbtchild.probe.DefaultsShim.makeResponseParams
import com.typesafe.sbtchild.probe.DefaultsShim.nameHandler
import com.typesafe.sbtchild.probe.DefaultsShim.runMainHandler
import com.typesafe.sbtchild.probe.DefaultsShim.testHandler
import com.typesafe.sbtchild.probe.DefaultsShim.watchTransitiveSourcesHandler
import sbt.Scoped.inputScopedToKey

/** This guy provides the support we need to run play projects... */
object PlaySupport {

  private def playRunHandler(taskName: String): RequestHandler = { (state: State, context: UIContext, params: Params) =>
    run(taskName, state, context, params)
  }

  private val shimInstaller = new ShimInstaller("play")

  private val findPlayHandler: PartialFunction[String, RequestHandler] = {
    case TaskNames.run => playRunHandler(TaskNames.run)
    case TaskNames.runMain => playRunHandler(TaskNames.runMain)
  }

  val findHandler: PartialFunction[String, RequestHandler] =
    findPlayHandler orElse DefaultsShim.findHandler

  // This is the shim'd run task we use instead of play's default.
  private val playRunShimTask = InputKey[Unit]("play-shim-run")

  def run(taskName: String, state: State, context: UIContext, params: Params): (State, Params) = {
    // TODO - Lookup default port and ensure it's ready/running....
    val s = SbtUtil.runInputTask(playRunShimTask, state, args = "", context = Some(context))
    (s, makeResponseParams(protocol.RunResponse(success = true,
      task = taskName)))
  }

  // TODO - Specify project too...
  def isPlayProject(state: State): Boolean = {
    val extracted = Project.extract(state)
    extracted.getOpt(SettingKey[Boolean]("play-plugin")).isDefined
  }

  def ensureShim(state: State): Unit = {
    // TODO - Detect SHA of shim file, and ensure we're up-to-date.
    if (isPlayProject(state))
      shimInstaller.ensure(state)
  }

}
