
var Play = {
		name: "Play monitor"
	  , tpl: _.template( $('#play_monitor_tpl').text() )

	  , init: function(v){
			$(window).on("keydown", v.view, function(e){
				if(!v.view.hasClass("active")) return true
				var a;
				// __(e.keyCode)
				switch(e.keyCode){
					case 40: // down
						a = $(".active", v.view).removeClass("active").next("dd")
						if (!a.length) a = $("dd", v.view).first()
						a.addClass("active")
						window.location.href = a[0].href
						break;
					case 38: // up
						a = $(".active", v.view).removeClass("active").prev("dd")
						if (!a.length) a = $("dd", v.view).last()
						a.addClass("active").focus()
						window.location.href = a[0].href
						break;
				}
			})
			$(window).on("keyup", v.view, function(e){
				if(!v.view.hasClass("active")) return true
				var a;
				// __(e.keyCode)
				console.log(e.keyCode)
				switch(e.keyCode){
					case 40: // down
					case 38: // up
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
			ƒ({})
		}
	}

  , Logs = {
		name: "Logs & errros"
	  , tpl: _.template( $('#play_logs_tpl').text() )

	  , init: function(v){
		}

	  , get: function(module, ƒ){
			ƒ(module)

		}
	}

  , AppMonitor = {
		name: "Application monitor"
	  , tpl: _.template( $('#play_app_monitor_tpl').text() )

	  , init: function(v){
		}

	  , get: function(module, ƒ){
			ƒ({})

		}
	}

  , Tests = {
		name: "Tests"
	  , tpl: _.template( $('#play_tests_tpl').text() )

	  , init: function(v){
		}

	  , get: function(module, ƒ){
			ƒ({})

		}
	}

  , SysMonitor = {
		name: "System monitor"
	  , tpl: _.template( $('#play_sys_monitor_tpl').text() )

	  , init: function(v){
		}

	  , get: function(module, ƒ){
			ƒ({})

		}
	}

  , RestConsole = {
		name: "Rest & Json console"
	  , tpl: _.template( $('#play_rest_console_tpl').text() )

	  , init: function(v){
		}

	  , get: function(module, ƒ){
			ƒ({})

		}
	}

