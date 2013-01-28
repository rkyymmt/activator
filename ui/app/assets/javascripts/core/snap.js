// Sort of MVC (Module, Grid, Router)
define([
  'core/plugin',
	'core/grid',
	'core/router'
	],function(plugins, Grid, router){

	var ko = req('vendors/knockout-2.2.1.debug'),
		key = req('vendors/keymage.min');

// Model for the whole app view
	var model = {
		snap: {
			appName: "Sample App",
			pageTitle: ko.observable()
		},
		plugins: plugins,
    router: router,
    grid: Grid
	};
	// TODO - initialize plugins...
	window.model = model;
	// Initialize the router now that everything is ready.
	$.each(plugins.list, function(idx, plugin) {
	router.registerRoutes(plugin.routes);
	});
	router.init();
	ko.applyBindings(model, window.body);

// All the magic here.
/*
Do
	.then( Router.parse, Module.load, Grid.render )
	.map( function(m){
		key.setScope(m[m.length-1].url.replace("/","."));
		m[m.length-1].view.addClass("active")
		return m;
	} )
	.when(function(n) {
		$(window).on('hashchange', n).trigger('hashchange')
	});*/

// End of define()
});
