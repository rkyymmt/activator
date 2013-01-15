require.config({
    baseUrl:	'/public',
    paths: {
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
    console.error(err.requireType);
    if (err.requireType === 'timeout') {
        console.log('modules: ' + err.requireModules);
    }
}

window.req = require

require([

	// Vendors
	'vendors/text',
	'vendors/css.min',
	'vendors/dust-core-0.6.0.min',
	'vendors/domReady',
	'vendors/jquery.min',
	'vendors/knockout-2.2.0',
	'vendors/chain'

],function(){

	require(window.preloadedPlugins, function(){

		require([

			// Core
			'core/grid',
			'core/header',
			'core/module',
			'core/navigation',
			'core/router',
			'core/utils',
			'core/main'

		],function(){
			//console.log( plugin('todo') )
		})
	})
})