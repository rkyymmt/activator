require([
	// Vendors
	'vendors/text',
	'vendors/css',
	'vendors/jquery-2.0.0b1',
	'vendors/chain',
	'vendors/keymage.min',
	'vendors/knockout-2.2.1.debug'
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
