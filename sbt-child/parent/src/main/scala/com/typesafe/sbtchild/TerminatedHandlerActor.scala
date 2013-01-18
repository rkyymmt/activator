package com.typesafe.sbtchild

import akka.actor._

// this just lets us stack Terminated actions.
trait TerminatedHandlerActor extends Actor {
  // when overriding, always chain up.
  def onTerminated(ref: ActorRef): Unit = {}
}
