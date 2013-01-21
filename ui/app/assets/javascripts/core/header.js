define(["core/header"], function(Header){

	var ko = req('vendors/knockout-2.2.0');

	var Header = function(){
		this.breadcrumb = ko.observableArray([]);
	}
	var h = new Header();
	ko.applyBindings(h, $("#breadcrumb")[0]);

	return {
		update: function(modules){
			var self = h;
			var root = "#";
			self.breadcrumb.removeAll();
			modules.map(function(m){
				if (m){
					self.breadcrumb.push({
						'title': m.module.title,
						'url': '#'+m.url
					});
				}
			});
		}
	}

})
