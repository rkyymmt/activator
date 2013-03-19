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

  private val playRunHandler: RequestHandler = run _

  // TODO - make a library to make checking shim plugins and contents easier...
  private val playPluginSbtContents = """addSbtPlugin("com.typesafe.builder" % "sbt-shim-play" % """" + APP_VERSION + "\")"

  private val PLAY_SHIM_FILE_NAME = "builder-play-shim.sbt"

  private lazy val playPluginSbtFile = {
    val tmp = java.io.File.createTempFile("play", "sbt-shim")
    IO.write(tmp, playPluginSbtContents)
    tmp.deleteOnExit()
    tmp
  }

  private lazy val playPluginSbtSha = Hashing.sha512(playPluginSbtFile)

  // Note: we import default shim for default behaviors and override with our own as necessary.
  import DefaultsShim._
  val findHandler: PartialFunction[String, RequestHandler] = {
    case TaskNames.name => nameHandler
    case TaskNames.discoveredMainClasses => discoveredMainClassesHandler
    case TaskNames.watchTransitiveSources => watchTransitiveSourcesHandler
    case TaskNames.compile => compileHandler
    case TaskNames.run => playRunHandler
    case TaskNames.runMain => playRunHandler
    case TaskNames.test => testHandler
  }

  // This is the shim'd run task we use instead of play's default.
  private val playRunShimTask = InputKey[Unit]("play-shim-run")

  def run(state: State, context: UIContext, params: Params): (State, Params) = {
    // TODO - Lookup default port and ensure it's ready/running....
    val s = SbtUtil.runInputTask(playRunShimTask, state, args = "", context = Some(context))
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
    // We can probably just check the build
    val extracted = Project.extract(state)
    val bd = extracted get (Keys.baseDirectory in ThisBuild)
    // Now, we need to check the SHA of the file against our sha...
    val shimPluginFile = bd / "project" / PLAY_SHIM_FILE_NAME
    def shimExists = shimPluginFile.exists
    def foundShimInBuild = extracted.getOpt(SettingKey[Boolean]("play-shim-installed")) getOrElse false
    def shimHashMatches = Hashing.sha512(shimPluginFile) == playPluginSbtSha
    // Note - We use found shim in build to see if we need to restart SBT due to plugin loading error...
    // It may be if everything is ok, but that one we should *FATALLY* error.
    shimExists && shimHashMatches && foundShimInBuild
  }

  def doShim(state: State): Unit = {
    val extracted = Project.extract(state)
    val bd = extracted get (Keys.baseDirectory in ThisBuild)
    // TODO - look this up...
    val shimPluginFile = bd / "project" / PLAY_SHIM_FILE_NAME
    // TODO - Don't hardcode group and name and stuff...
    //  We should generate this file as part fo the build.  THEN - we should also
    // take the SHA of the shim file to make sure we're up-to-date...
    IO.copyFile(playPluginSbtFile, shimPluginFile, false)
  }

  def ensureShim(state: State): Unit = {
    // TODO - Detect SHA of shim file, and ensure we're up-to-date.
    if (isPlayProject(state) && !isPlayShimInstalled(state)) {
      doShim(state)
      System.err.println("Installed Play Support, need to reboot SBT.")
      // By Erroring out (and doing so before responding to protocol method),
      // We force the Sbt process to reload and try again...
      sys.error("Installed Play Support, need to reboot SBT.")
    }
  }

}
