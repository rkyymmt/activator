// Sort of MVC (Module, Grid, Router)
define([
	'vendors/knockout-2.2.0',
	'core/module',
	'core/grid',
	'core/router',
	'core/header'
	],function(ko, Module, Grid, Router, Header){

// Model for the whole app view
	var model = {
		snap: {
			appName: "Sample App",
			pageTitle: ko.observable()
		},
		plugins: Router.plugins
	};
	ko.applyBindings(model, $("body > nav")[0]);

// All the magic here.
Do
	.then( Router.parse, Module.load, Grid.render )
	.map( Header.update )
	.when(function(n) {
		$(window).on('hashchange', n).trigger('hashchange')
	});

// End of define()
});
