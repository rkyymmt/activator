package com.typesafe.sbt

import _root_.sbt._

import Project.Initialize
import Keys._
import Defaults._
import Scope.GlobalScope

object SbtChild extends Plugin {
  override lazy val settings = Seq.empty

  ///// Settings keys

  object SbtChildKeys {

  }

  val listen = Command.command("listen", Help.more("listen", "listens for remote commands")) { origState =>
    // FIXME
    origState
  }
}
