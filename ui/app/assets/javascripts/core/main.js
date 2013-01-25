var Raindrop = (function(){

		// All loaded and displayed modules
	var loaded = [],
		// Decomposed url in an array
		breadcrumb = [],

		routes = {
			'monitor':				[ "todo/todo" , {
				'logs':				[ "todo/todo" ],
				'tests':			[ "todo/todo" ],
				'application':		[ "todo/todo" ],
				'system':			[ "todo/todo" ],
				'rest-console':		[ "todo/todo" ]
			}],
			'console':				[ "demo/demo" , {
				':id':				[ "todo/todo" , {
					'dashboard':	[ "todo/todo" ],
					'tasks':		[ "todo/todo" ],
					'code':			[ "todo/todo" ],
					'documents':	[ "todo/todo" ],
					'people':		[ "todo/todo" ],
					'options':		[ "todo/todo" ]
				}],
				'create':			[ "todo/todo" ]
			}],
			'code':					[ "code/browse" , {
				'commits': 			[ "todo/todo" ],
				'branches':			[ "todo/todo" ],
				'comments':			[ "todo/todo" ],
				'wiki':				[ "todo/todo" ],
				':id':				[ "code/browse" , {
					':id':			[ "code/browse" , {':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse"]}]}]}]}]}]}]}]}]}]}]}]}]}]}]}]}]
				}]
			}],
			':id':					[ "todo/todo" ]
		},

		// On new request
		request = function(n) {
			$(window).on('hashchange', n).trigger('hashchange')
		},

		// From the breadcrumb, checks route synthax and get module
		match = function(bc, routes, modules){
			var url = bc.shift(),
				i = modules.length,
				// Check: module name or string parameter
				// Module is a tuple
				_module = routes[url] || routes[':id'] // TODO: w/ Regex
			if( _module && loaded[i] && loaded[i].url == url ){
					modules[i] = loaded[i]
				if(_module[1] && bc.length){
					modules = match( bc, _module[1], modules )
				} else {
					// Add extra loaded modules
					modules = modules.concat(loaded.slice(modules.length))
				}
			} else if ( _module ){
				modules[i] = {
					pluginID: _module[0],
					args: {
						full: breadcrumb,
						next: bc.slice(0),
						before: i > 0 ? modules[i-1].url : '',
						path: i > 0 ? modules[i-1].args.path +'/'+ url : url
					},
					index: i,
					// recompose the url from previous object, #weird
					url: url
				}
				if(_module[1] && bc.length){
					modules = match( bc, _module[1], modules )
				}
			} else {
				modules[i] = {
					url: '404',
					plugin: "404",
					index: i
				}
			}
			// This object refers all modules from url
			// Even those who may already be loaded
			return modules
		}

	return {
		init: function(){
			Do.map(function(v){
					// Split full path in modules
					breadcrumb = /^#?\/?(.+)\/?$/.exec(window.location.hash)[1].split("/")
					// Check if modules are loaded, or retrieve module object
					loaded = match(breadcrumb, routes, [])
					return loaded
				})
				.then( Module.load, Grid.render, Header.update )
				.when(request)
		},
		request: request
	}

})();


// MagicTrick()
$(function(){

	Grid.init()
	Header.init()
	req('core/navigation').init()
	Raindrop.init()

	var ko = req('vendors/knockout-2.2.0')

})

/*

	$("#wrapper").on("click", "article.list li, article.list dd", function(e){
		var el = $(this).closest("li, dd")
		window.location = el.find("a").attr("href")
		el.addClass("active").siblings().removeClass("active")
	})


*/
