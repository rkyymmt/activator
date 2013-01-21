define(['css!plugins/code/code','text!plugins/code/browse2.html'], function(css, template){

	var ko = req('vendors/knockout-2.2.0');

	var Browser = Class({
		title: ko.observable("Browse code"),
		tree: ko.observableArray(),
		path: ko.observableArray([]),
		init: function(parameters){
		},
		render: function(parameters){
			var view = $(template);
			this.update(parameters);
			ko.applyBindings(this, view[0]);
			return view;
		},
		update: function(parameters){
			var path = parameters.args.rest,
				url = path.length?"./"+path.join("/"):"./";
			this.load(url);
			this.breadcrumb(path);
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
		breadcrumb: function(path){
			var self = this,
				root = "#demo/";
			this.path.removeAll();
			this.path.push({
				title: 'home',
				url: root
			});
			path.map(function(f){
				if (f){
					root += f +"/";
					self.path.push({
						'title': f,
						'url': root
					});
				}
			});
		},
		open: function(e){
			var target = e.location.replace("/Users/iamwarry/Work/Typesafe/Builder/snap/", "")
			window.location.hash = "demo/" + target;
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

});