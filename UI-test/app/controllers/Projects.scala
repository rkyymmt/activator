package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.JsObject
import models._

object Users extends Controller {

	def all = Action {
		Ok( Json.toJson( User.findAll() ) )
	}

	def add = Action(parse.json) { request =>
		Ok( Json.toJson( User.save( request.body.as[User] ) ) ).as("application/json")
	}

	def get(id: String) = Action {
		Ok( Json.toJson( User.findById(id) ) ).as("application/json")
	}

	def edit(id: String) = Action(parse.json) { request =>
		Ok( Json.toJson( User.save( request.body.as[User] ) ) ).as("application/json")
	}

	def delete(id: String) = Action {
		User.delete(id)
		Ok( JsNull  ).as("application/json")
	}

}

object Projects extends Controller {

	def all = Action {
		Ok( Json.toJson( Project.findAll() ) )
	}

	def add = Action(parse.json) { request =>
		Ok( Json.toJson( Project.save( request.body.as[Project] ) ) ).as("application/json")
	}

	def get(id: Long) = Action {
		Ok( Json.toJson( Project.findById(id) ) ).as("application/json")
	}

	def edit(id: Long) = Action(parse.json) { request =>
		Ok( Json.toJson( Project.save( request.body.as[Project] ) ) ).as("application/json")
	}

	def delete(id: Long) = Action {
		Project.delete(id)
		Ok( JsNull  ).as("application/json")
	}

	def users(id: Long) = Action {
		Ok( Json.toJson( Project.users(id) ) ).as("application/json")
	}

}

