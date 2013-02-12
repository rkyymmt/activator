require.config({
	baseUrl:	'/public',
	paths: {
		// Common paths
		vendors:	'javascripts/vendors',
		core:		'javascripts/core',
		plugins:	'plugins'
	},
	map: {
		'*': {
			'text':	'vendors/text',
			'css':	'vendors/css.min'
		}
	}
})

require.onError = function (err) {
	if (err.requireType === 'timeout') {
		console.log('modules: ' + err.requireModules);
	}
}

window.req = require

require([
	// Vendors
	'vendors/text',
	'vendors/css.min',
	'vendors/jquery-2.0.0b1',
	'vendors/chain',
	'vendors/keymage.min',
	'vendors/knockout-2.2.1.debug'
],function() {
	require(['core/templates'], function() {
	require([
		// Core
		'core/utils',
		'core/sbt'
	], function() {
		require(['core/snap'])
	})
	})
})
