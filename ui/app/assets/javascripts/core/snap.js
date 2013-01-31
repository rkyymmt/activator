// Sort of MVC (Module, Grid, Router)
define(['./plugin', './grid', './router', './streams'], function(plugins, Grid, router, Streams) {

	var ko = req('vendors/knockout-2.2.1.debug'),
		key = req('vendors/keymage.min');

	// Model for the whole app view
	var model = {
		snap: {
			// TODO - This should be obversvable and we get notified of changes by sbt....
			appName: window.serverAppModel.name ? window.serverAppModel.name : window.serverAppModel.id,
			pageTitle: ko.observable()
		},
		plugins: plugins,
		router: router,
		grid: Grid,
		streams: Streams,
		// This is the initialization of the application...
		init: function() {
			var self = this;
			// TODO - initialize plugins...
			$.each(self.plugins.list, function(idx,plugin) {
				self.router.registerRoutes(plugin.routes);
			});
			self.router.init();
			ko.applyBindings(self, window.body);
			return self;
		}
	};
	window.model = model.init();
});
