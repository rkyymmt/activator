// Sort of MVC (Module, Grid, Router)
define(['./plugin', './router', './pluginapi', './navigation', './tutorial/tutorial', './streams'], function(plugins, router, api, navigation, Tutorial, streams) {

	var ko = api.ko;

	// Register webSocket error handler
	streams.subscribe({
		handler: function(event) {
			alert("Connection lost; you will need to reload the page or restart Activator. It's also possible that Activator is open in another tab, which causes this error.");
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
		// This is the initialization of the application...
		init: function() {
			var self = this;
			self.widgets = [];
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
		tutorial: new Tutorial()
	};
	// TODO - Don't bleed into the window.
	window.model = model.init();
	return model;
});
