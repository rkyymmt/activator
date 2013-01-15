var Raindrop = (function(){

		// All loaded and displayed modules
	var loaded = [],
		// Decomposed url in an array
		breadcrumb = [],

		// On new request
		request = function(n) {
			$(window).on('hashchange', n).trigger('hashchange')
		},

		// From the breadcrumb, checks route synthax and get module
		match = function(bc, Router){
			var url = bc.shift(),
				// Check: module name or string parameter
				module = Router[url] || Router[':id'] // TODO: w/ Regex
			if( loaded[loaded.length] && loaded[loaded.length].url == url ){
				loaded =  !!module && !!bc.length ? match( bc, module[1] ) : loaded
			} else if ( module ){
				loaded[loaded.length] = {
					plugin: module[0],
					args: {
						full: breadcrumb,
						next: bc.slice(0),
						before: loaded.length > 0 ? loaded[loaded.length-1].url : '',
						current: url
					},
					index: loaded.length,
					// recompose the url from previous object, #weird
					url: loaded.length > 0 ? loaded[loaded.length-1].url +'/'+ url : url
				}
				loaded =  !!bc.length ? match( bc, module[1], loaded ) : loaded
			} else {
				loaded[loaded.length] = {
					url: '404',
					plugin: "404",
					index: loaded.length
				}
			}
			// This object refers all modules from url
			// Even those who may already be loaded
			return loaded
		}

	return {
		init: function(){
			Do.map(function(v){
					v = window.location.hash.substr(1)
					// Split full path in modules
					breadcrumb = v.search("/") < 0 ? [v] : v.split('/')
					// Clear extra loaded modules
					// but we keep the first ones in case it matches the url
					loaded = loaded.splice(breadcrumb.length, -1)
					// Check if modules are loaded, or retrieve module object
					return match(breadcrumb.slice(0), Router)
				})
				.then( Module.update, Grid.update, Header.update )
				.when(request)
		},
		request: request
	}

})();


// MagicTrick()
$(function(){

	Grid.init()
	Header.init()
	Navigation.init()
	Raindrop.init()

})

/*

	$("#wrapper").on("click", "article.list li, article.list dd", function(e){
		var el = $(this).closest("li, dd")
		window.location = el.find("a").attr("href")
		el.addClass("active").siblings().removeClass("active")
	})


*/
