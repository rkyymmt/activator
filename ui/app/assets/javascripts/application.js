require.config({
	baseUrl:	'/public',
	// hack for now due to problem loading plugin loaders from a plugin loader
	map: {
		'*': {
			'css': '../../webjars/require-css/0.0.7/css',
			'text': '../../webjars/requirejs-text/2.0.10/text'
		}
	},
	paths: {
		core:		'javascripts/core',
		plugins:	'plugins'
	}
});

require([
	// Vendors
	'../../webjars/requirejs-text/2.0.10/text',
	'../../webjars/require-css/0.0.7/css',
	'webjars!jquery',
	'webjars!knockout',
	'webjars!keymage'
],function() {
	require(['core/templates'], function() {
	require([
		'core/effects',
		// Core
		'core/utils',
		'core/sbt'
	], function() {
		require(['core/snap'])
	})
	})
})
