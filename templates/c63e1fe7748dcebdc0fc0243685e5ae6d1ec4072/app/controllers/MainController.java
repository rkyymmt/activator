package controllers;

import play.mvc.Controller;
import play.mvc.Result;
import views.html.index;

public class MainController extends Controller {
    
    public static Result index() {
        return ok(index.render("Hello from Java"));
    }
    
}
