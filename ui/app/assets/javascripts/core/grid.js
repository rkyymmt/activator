// The grid handles the pannels: Templating & Positioning
var Grid = {

	elements: {}, // jQuery objects cached

	align: function(){
  		// TODO: Scroll to active pannel
		$("#wrapper").scrollLeft(9e9)
	},

	// Responsive grid
	resize: function(){
		var screen = Grid.elements.wrapper.width(),
			size = 	screen < 660 ? 1  :
					screen < 990 ? 2  :
					screen < 1320 ? 3 :
					screen < 1650 ? 4 :
					screen < 1980 ? 5 : 0
		Grid.elements.body.attr("data-width", size)
		Grid.align()
	},

	// Called when data is loaded.
	// Does the rendering
	update: Action(function(modules,n){
		modules = modules.map(function(module){
			var container = $("#wrapper > *").eq(module.index)
			if ( container.data('url') ==  module.url) {
				module.view = container
			} else {
				if(!module.plugin.render) throw("Plugins needs a render method.")
				module.view = $(module.plugin.render(module))
					.data('url', module.url) // really necessary?
					.css("z-index", 100 - module.index )
				container = !!container.length ? container.replaceWith(module.view) : module.view.addClass("fadein").appendTo("#wrapper")
			}
			return module
		})
		!modules.length || modules[modules.length-1].view.nextAll().remove()
		Grid.align()
		n(modules)
	}),

	init: function(){
		Grid.elements.body = $("body")
		Grid.elements.wrapper = $("#wrapper")

		// PLACEHOLDER
		$(window).on("keyup", function(e){
			var a;
			switch(e.keyCode){
				case 37: // left
					var a = $("#wrapper > article.active").removeClass("active").prev("article")
					if (a.length) a.addClass("active").find("a")[0].focus()
					else $("#wrapper > nav").addClass("active")
					break;
				case 39: // right
					window.location.href = $(".active .active h2 a")[0].href
					var a = $("#wrapper > article.active").removeClass("active").next("article")
					if (a.length) a.addClass("active").find("a")[0].focus()
					else $("#wrapper > article").first().addClass("active")
					break;
			}
		}).on("resize", Grid.resize).trigger("resize")
	}

}
