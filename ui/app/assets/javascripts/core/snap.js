// Sort of MVC (Module, Grid, Router)
define(['./plugin', './grid', './router', './pluginapi', './navigation', './tutorial/tutorial', './streams'], function(plugins, Grid, router, api, navigation, Tutorial, streams) {

	var ko = api.ko,
		key = api.key;

	// Register webSocket error handler
	streams.subscribe({
		handler: function(event) {
			alert("Connection lost; you will need to reload the page or restart Builder");
		},
		filter: function(event) {
			return event.type == streams.WEB_SOCKET_CLOSED;
		}
	});

	// Model for the whole app view
	var model = {
		snap: {
			// TODO - This should be observable and we get notified of changes by sbt....
			appName: window.serverAppModel.name ? window.serverAppModel.name : window.serverAppModel.id,
			pageTitle: ko.observable(),
			activeWidget: api.activeWidget
		},
		plugins: plugins,
		router: router,
		grid: Grid,
		// This is the initialization of the application...
		init: function() {
			var self = this;
			self.widgets = [];
			self.newsHtml = ko.observable("<div></div>");
			// TODO - initialize plugins in a better way perhaps...
			$.each(self.plugins.list, function(idx,plugin) {
				self.router.registerRoutes(plugin.routes);
				$.each(plugin.widgets, function(idx, widget) {
					self.widgets.push(widget);
				});
			});
			self.router.init();
			ko.applyBindings(self, window.body);
			navigation.init();
			return self;
		},
		api: api,
		tutorial: new Tutorial(),
		setNewsJson: function(jsonString) {
			console.log("setting news json to ", typeof(jsonString), jsonString);
			var json = JSON.parse(jsonString);
			if ('html' in json) {
				this.newsHtml(json.html);
			} else {
				console.error("json does not have an html field");
			}
		},
		loadNews: function() {
			var areq = {
				url: "http://downloads.typesafe.com/typesafe-builder/" + window.serverAppModel.appVersion,
				type: 'GET',
				jsonpCallback: 'setNewsJson',
				dataType: 'jsonp' // return type
			};

			console.log("sending ajax news request ", areq)
			return $.ajax(areq);
		}
	};
	window.model = model.init();

	// jsonp us up some news
	window.setNewsJson = model.setNewsJson.bind(model);
	model.loadNews();

	return model;
});
