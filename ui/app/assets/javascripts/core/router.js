define(window.preloadedPlugins,function(){

	// All loaded and displayed modules
var loaded = [],
	// Available plugins
	plugins = [].slice.call(arguments),
	// Decomposed url in an array
	breadcrumb = [],

	// Routes are nested:
	// " /projects/:id/code/ " would call each related route
	// And each route is associated with a requirejs plugin
	// Example:
	//  {
	//      'foo':					[ "foo/foo" , {
	//          'bar':				[ "foo/bar" , ":rest" ],
	//          ':id':				[ "foo/foobar" ]
	//      }]
	//  }
	// Note
	// :rest will redirect all /foo/bar/.* urls
	routes = (function(r){
		// Get plugins' routes
		plugins.map(function(p){
			if (p.routes) r = $.extend(r, p.routes)
		})
		return r
	}({})),

	// From the breadcrumb, checks route synthax and get module
	match = function(bc, routes, modules){
		var rest = bc.slice(1)
			url = bc.shift(),
			i = modules.length,
			args = {
				full: breadcrumb,
				rest: rest,
				before: i > 0 ? modules[i-1].url : '',
				path: i > 0 ? modules[i-1].args.path +'/'+ url : url
			}
			// Check: module name or string parameter
			_module = routes[url] || routes[':id'] // TODO: w/ Regex

		if (routes == ":rest") return modules
		if ( _module && loaded[i] && loaded[i].url == url ){
			modules[i] = loaded[i]
			modules[i].args = args
			if(_module[1] && bc.length){
				modules = match( bc, _module[1], modules )
			} else {
				// Add extra loaded modules
				// modules = modules.concat(loaded.slice(modules.length))
			}
		} else if ( _module ){
			modules[i] = {
				pluginID: _module[0],
				index: i,
				// recompose the url from previous object, #weird
				url: url
			}
			modules[i].args = args
			if(_module[1] && bc.length){
				modules = match( bc, _module[1], modules )
			}
		} else {
			modules[i] = {
				url: '404',
				pluginID: 'errors/404',
				index: i
			}
		}
		// This object refers all modules from url
		// Even those who may already be loaded
		return modules
	}

	return {
		// returns an Array of module ids (requirejs ids)
		parse: Action(function(url, n){
			// If no arguments, take the hash
			url = url || window.location.hash
			// Split full path in modules
			breadcrumb = /^#?\/?(.+)\/?$/.exec(window.location.hash)[1].split("/")
			// Check if modules are loaded, or retrieve module object
			loaded = match(breadcrumb.slice(0), routes, [])
			n(loaded)
		}),
		// Register a plugin's routes
		registerRoutes: function(label, routes){
			routes[label] = routes
		},
		plugins: plugins,
		modules: loaded
	}

})

