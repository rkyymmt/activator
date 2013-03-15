package com.typesafe.sbtchild

import _root_.sbt._
import Project.Initialize
import Keys._
import Defaults._
import Scope.GlobalScope
import com.typesafe.sbtchild._
import sbt.Aggregation.KeyValue
import sbt.complete.DefaultParsers
import sbt.Load.BuildStructure

object SbtUtil {

  def extractWithRef(state: State): (Extracted, ProjectRef) = {
    val ref = Project.extract(state).currentRef
    (Extracted(Project.structure(state), Project.session(state), ref)(Project.showFullKey), ref)
  }

  def extract(state: State): Extracted = {
    extractWithRef(state)._1
  }

  def runInputTask[T](key: ScopedKey[T], state: State, args: String): State = {
    val extracted = extract(state)
    implicit val display = Project.showContextKey(state)
    val it = extracted.get(SettingKey(key.key) in key.scope)
    val keyValues = KeyValue(key, it) :: Nil
    val parser = Aggregation.evaluatingParser(state, extracted.structure, show = false)(keyValues)
    // we put a space in front of the args because the parsers expect
    // *everything* after the task name it seems
    DefaultParsers.parse(" " + args, parser) match {
      case Left(message) =>
        throw new Exception("Failed to run task: " + display(key) + ": " + message)
      case Right(f) =>
        f()
    }
  }

  def makeAppendSettings(settings: Seq[Setting[_]], inProject: ProjectRef, extracted: Extracted) = {
    // transforms This scopes in 'settings' to be the desired project
    val appendSettings = Load.transformSettings(Load.projectScope(inProject), inProject.build, extracted.rootProject, settings)
    appendSettings
  }

  def reloadWithAppended(state: State, appendSettings: Seq[Setting[_]]): State = {
    val session = Project.session(state)
    val structure = Project.structure(state)
    implicit val display = Project.showContextKey(state)

    // reloads with appended settings
    val newStructure = Load.reapply(session.original ++ appendSettings, structure)

    // updates various aspects of State based on the new settings
    // and returns the updated State
    Project.setProject(session, newStructure, state)
  }
}
