
var Projects = {
		name: "Projects"
	  , tpl: _.template( $('#projects_tpl').text() )

	  , init: function(v){
			$(window).on("keydown", v.view, function(e){
				if(!v.view.hasClass("active")) return true
				var a;
				switch(e.keyCode){
					case 40: // down
						a = $(".active", v.view).removeClass("active").next("dd")
						if (!a.length) a = $("dd", v.view).first()
						a.addClass("active").find("a")[0].focus()
						return false;
						break;
					case 38: // up
						a = $(".active", v.view).removeClass("active").prev("dd")
						if (!a.length) a = $("dd", v.view).last()
						a.addClass("active").find("a")[0].focus()
						return false;
						break;
				}
			})
			$(window).on("keyup", v.view, function(e){
				if(!v.view.hasClass("active")) return true
				var a;
				// __(e.keyCode)
				switch(e.keyCode){
					case 40: // down
					case 38: // up
						window.location.href = $(".active", v.view).find("a")[0].href
						break;
					case 37: // left
						break;
					case 39: // right
						window.location.href = $(".active h2 a", v.view)[0].href
						break;
					case 191: // slash
						break;
				}
			})
		}

	  , get: function(module, ƒ){
			$.getJSON( '/' + module.url, ƒ)
		}
	}

  , Project = {
		name: "Project"
	  , tpl: _.template( $('#project_tpl').text() )

	  , init: function(v){
		}

	  , get: function(module, ƒ){
			$.getJSON( '/' + module.url, ƒ)
		}
	}

