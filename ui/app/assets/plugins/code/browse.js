define(['css!./code.css','text!./browse.html'], function(css, template){

	var ko = req('vendors/knockout-2.2.0'),
		key = req('vendors/keymage.min');

	var Browser = Class({
		init: function(parameters){
		  this.title = ko.observable("Browse code");
			this.tree = ko.observableArray([]);
      // TODO - initialize from breadcrumbs?
		},
		render: function(parameters){
			var view = $(template + " ");
			this.view = view[0];

			var url = "./" + parameters.args.path.replace(/^code\/?/,"")
			this.load(url);
			this.title("Browse: "+url)

			this.view.id = parameters.args.path.replace("/", "");
			ko.applyBindings(this, this.view);

			// TODO : Refine key api
			key(parameters.url.replace("/","."), 'up', function(e){
				var target = $("dd.active, li.active", view)
					.prev()
					.getOrElse("dd:last, li:last", view)
					.addClass("active")

				target
					.siblings()
					.removeClass("active");

				// To autoscroll to link
				target.find("a")[0].focus();
			}, {preventDefault: true});
			key(parameters.url.replace("/","."), 'down', function(e){
				var target = $("dd.active, li.active", view)
					.next()
					.getOrElse("dd:first, li:first", view)
					.addClass("active")

				target
					.siblings()
					.removeClass("active");

				// To autoscroll to link
				target.find("a")[0].focus();
			}, {preventDefault: true});
			key(parameters.url.replace("/","."), 'left', function(e){
				var target = $("nav a", view).eq(-2)
						.getOrElse("nav a:first-child", view)
					window.location.hash = target.attr("href")
			});
			key(parameters.url.replace("/","."), 'right', function(e){
				var target = $("dd.active, li.active", view)
					.trigger("click");
			});

			return view;
		},
		update: function(parameters){
			console.log(parameters)
		},
		load: function(url){
			var self = this;
			fetch(url)
				.done(function(datas){
					self.tree.removeAll();
					for (var i in datas.children){
						self.tree.push( datas.children[i] );
					}
				})
				.fail(function(){
					console.error("Render failed");
				});
		},
		open: function(e){
			var target = e.location.replace(window.appLocation, "")
			window.location.hash = "code/" + target;
			return false;
		}
	});

	// Fetch utility
	function fetch(url){
		return $.ajax({
			url: '/api/local/browse',
			type: 'GET',
			dataType: 'json',
			data: { location: url }
		});
	}

	return Browser;

})

