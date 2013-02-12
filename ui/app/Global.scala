import play.api._

object Global extends GlobalSettings {
  override def onStop(app: Application) {
    super.onStop(app)
    Logger.info("onStop received")
    snap.AppManager.onApplicationStop()
  }
}
