// The grid handles the views, and pannels (Templating & Positioning)
define(["plugins/tutorial/tutorial"], function(Tutorial){

var ko = req('vendors/knockout-2.2.0'),
	key = req('vendors/keymage.min');


var elements = {}, // jQuery objects cached

	align = function(){
  		// TODO: Scroll to active pannel
		$("#wrapper").scrollLeft(9e9);
	},

	// Responsive grid
	resize = function(){
		var screen = elements.wrapper.width(),
			size = 	screen < 660 ? 1  :
					screen < 990 ? 2  :
					screen < 1320 ? 3 :
					screen < 1650 ? 4 :
					screen < 1980 ? 5 : 0
		elements.body.attr("data-width", size);
		align();
	},

	init = function(){
		elements.body = $("body")
		elements.wrapper = $("#wrapper")

		// PLACEHOLDER
		$(window)
			.on("resize", resize)
			.trigger("resize");
	}

	// Since it's a view... we do need the DOM to be loaded
	$(init);

return {
	// Called when data is loaded.
	// Does the rendering
	render: Action(function(modules,n){
		modules = modules.map(function(module){
			var container = $("#wrapper > *").eq(module.index);
			if ( container.data('url') ==  module.url) {
				!module.module.update || module.module.update(module);
			} else {
				module.view = $(module.module.render(module))
					.data('url', module.url) // really necessary?
					.css("z-index", 100 - module.index );
				container = !!container.length ? container.replaceWith(module.view) : module.view.addClass("fadein").appendTo("#wrapper");
			}
			return module;
		});
		!modules.length || modules[modules.length-1].view.nextAll().remove();
		align();
		n(modules);
	})
}

});
