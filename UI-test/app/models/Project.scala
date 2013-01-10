package models

import play.api.libs.json._
import play.api.libs.json.JsObject
import cagette._

// Projects
case class Project(id: Long, name: String)
object Project extends Cageot[Project,Long] {

	implicit object ProjectFormat extends Format[Project] {
		def reads(json: JsValue): Project = Project(
			(json \ "id").as[Long],
			(json \ "name").as[String]
		)
		def writes(p: Project): JsValue = JsObject(Seq(
			"id" -> JsNumber(p.id),
			"name" -> JsString(p.name),
			"updated" -> Json.toJson(Map("date" -> "Today", "user" -> "maxime"))
		))
	}

	override def initialData = Seq(
		Project(1, "Raindrop"),
		Project(2, "Secret"),
		Project(3, "Playmate"),
		Project(4, "Play"),
		Project(5, "Zaptravel"),
		Project(6, "Boilerplate"),
		Project(7, "Test"),
		Project(8, "Revolution"),
		Project(9, "Facebook")
	)

	def users(id: Long) = {
		User.findBy(_.projects.contains(id))
	}

}

// Users
case class User(email: String, name: String, groups: Seq[String], projects: Seq[Long])
object User extends Cageot[User,String]()(Identifier(_.email)){

	implicit object UserFormat extends Format[User] {
		def reads(json: JsValue): User = User(
			(json \ "email").as[String],
			(json \ "name").as[String],
			(json \ "groups").as[Seq[String]],
			(json \ "projects").as[Seq[Long]]
		)
		def writes(u: User): JsValue = JsObject(Seq(
			"email" -> JsString(u.email),
			"name" -> JsString(u.name),
			"groups" -> JsArray(u.groups.map(JsString(_))),
			"projects" -> JsArray(u.projects.map(JsNumber(_)))
		))
	}

	override def initialData = Seq(
		User("hello@warry.fr", "Maxime",Seq("admin", "user"), Project.findAll().map(_.id)),
		User("kiki@gmail.com", "Kiki",Seq("user"), Seq(4,5,6)),
		User("toto@gmail.com", "Toto",Seq("user"), Seq(1,5,7))
	)

}

