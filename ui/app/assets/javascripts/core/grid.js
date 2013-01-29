// The grid handles the views, and pannels (Templating & Positioning)
define(function() {

	var ko = req('vendors/knockout-2.2.1.debug'),
		key = req('vendors/keymage.min');


	var elements = {},
		// jQuery objects cached
		align = function() {
			// TODO: Scroll to active pannel
			$("#wrapper").scrollLeft(9e9);
		},

		// Responsive grid
		resize = function() {
			var screen = elements.wrapper.width(),
				size = screen < 660 ? 1 : screen < 990 ? 2 : screen < 1320 ? 3 : screen < 1650 ? 4 : screen < 1980 ? 5 : 0
				elements.body.attr("data-width", size);
			align();
		},

		init = function() {
			elements.body = $("body")
			elements.wrapper = $("#wrapper")

			function keyHandler(e) {
				var current = $("#wrapper > .active"),
					target;

				if(e.keyIdentifier == "Left") {
					target = current.prev()
				} else {
					target = current.next()
				}

				if(target.length) {
					current.removeClass("active")
					target.addClass("active")[0].scrollIntoView();
					key.setScope(target.attr("data-scope"));
				}
			}

			key('left', keyHandler, {
				preventDefault: true
			});
			key('right', keyHandler, {
				preventDefault: true
			});

			// PLACEHOLDER
			$(window).on("resize", resize).trigger("resize");
		}

		// Since it's a view... we do need the DOM to be loaded
		$(init);

	return {
		// This is passed the elements added *for one of the modules on the screen* and the data *associated with that template*.
		// Because each is a breadcrumb, we can pull the index from that and add any flashy stuff we want.
		afterRender: function(elements, data) {
			var modules = $(elements).filter('div');
			modules.each(function(idx, module) {
				$(module).css("z-index", 100 - data.index).attr("data-scope", data.url.replace(/\//g, "."))
			});
			if(!$("#wrapper > .active").length) {
				$("#wrapper > div").last().addClass("active")
				key.setScope(data.args.path.replace(/\//g, "."))
			}
			align();
		},
		// This is called everytime an element is removed. Currently a no-op.
		beforeRemove: function(el, idx, module) {
			var wasActive = $(el).hasClass("active")
			// Note: WE HAVE TO REMOVE THE ELEMENT!
			$(el).remove();
			if(wasActive) {
				$("#wrapper > div").last().addClass("active")
				key.setScope(module.args.before.replace(/\//g, "."))
			}
		},
		// This is called after an element is added to the array.  We get the rendered template, and the breadcrumb/module data
		// associated with the widget.
		afterAdd: function(el, idx, module) {
			$(el).css("z-index", 100 - idx);
			// TODO - Should we auto move key-scope + active?
			align();
		}
	}

});