var Grid = {

	elements: {}
  , list: []

  , align: function(){
		$("#wrapper").scrollLeft(9e9)
	}

  , resize: function(){
		var screen = Grid.elements.wrapper.width()
		  , size = 	screen < 660 ? 1  :
					screen < 990 ? 2  :
					screen < 1320 ? 3 :
					screen < 1650 ? 4 :
					screen < 1980 ? 5 : 0
		Grid.elements.body.attr("data-width", size)
		Grid.align()
	}

  , update: Action(function(v,n){
		console.log("Grid UPDATE", v)
		v = _.map(v, function(module){
			var container = $("#wrapper > *").eq(module.index)
			if ( container.data('module') && container.data('module').url ==  module.url) {
				console.log("Grid CACHE",module.module.name )
				module.view = container
			} else {
				console.log("Grid RENDER",module.module.name, module.data )
				module.view = $( module.module.tpl(data=module.data) )
					.data('module', module)
					.css("z-index", 100 - module.index )

				container = !!container.length ? container.replaceWith(module.view) : module.view.addClass("fadein").appendTo("#wrapper")
				module.module.init(module)
			}
			return module
		})
		!v.length || v[v.length-1].view.nextAll().remove()
		Grid.align()
		n(v)
	})

  , init: function(){
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

