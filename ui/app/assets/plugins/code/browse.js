define(['css!./code','text!./browse.html'], function(css, template){

	var ko = req('vendors/knockout-2.2.0')

	// Fetch
	function fetch(url){
		return $.ajax({
			url: '/api/local/browse',
			type: 'GET',
			dataType: 'json',
			data: { location: url }
		})
	}

	var Browser = function(url){
		var self = this
		self.title = "Browse: "+url
		self.tree = ko.observableArray([])
		if(!self.tree.length) self.load(url, self.tree)
	}
	Browser.prototype = {
		load: function(url, parent){
			fetch(url)
				.done(function(datas){
					for (var i in datas.children){
						parent.push( datas.children[i] )
					}
				})
				.fail(function(){
					console.error("Render failed")
				})
		},
		open: function(e){
			var target = e.location.replace("/Users/iamwarry/Work/Typesafe/Builder/snap/", "")
			window.location.hash = "code/browse/" + target
			return false;
		}
	}

	// Render
	function render(parameters){
		var url = "."+parameters.url.replace("code/browse", ""),
			view = $(template).attr("YOOOO", url)
		ko.applyBindings(new Browser(url), view[0])
		return view
	}

	return {
		name: "Browse",
		id: "demo",
		render: render
	}
})

