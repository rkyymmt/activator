// Sort of MVC (Module, Grid, Router)
define([
	'core/module',
	'core/grid',
	'core/router'
	],function(Module, Grid, Router){

	var ko = req('vendors/knockout-2.2.0'),
		key = req('vendors/keymage.min');

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
	.map( function(m){
		key.setScope(m[m.length-1].url.replace("/","."));
		m[m.length-1].view.addClass("active")
		return m;
	} )
	.when(function(n) {
		$(window).on('hashchange', n).trigger('hashchange')
	});

// End of define()
});
