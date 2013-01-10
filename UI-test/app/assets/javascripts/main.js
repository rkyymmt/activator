var Raindrop = (function(){

		// All loaded and displayed modules
	var loaded = []
	  , breadcrumb = [] // Decomposed url in an array

		// On new request
	  , request = function(n) {
			$(window).on('hashchange', function (e) {
				n( window.location.hash.substr(1) )
			}).trigger('hashchange')
		}

		// From the breadcrumb, checks route synthax and get module
	  , match = function(breadcrumb, Router){
			var url = breadcrumb.shift()
				// Check: module name or string parameter
			  , module = Router[url] || Router[':id'] // TODO: w/ Regex

			if( loaded[loaded.length] && loaded[loaded.length].url == url ){
				loaded =  !!module && !!breadcrumb.length ? match( breadcrumb, module[1] ) : loaded
			} else if ( module ){
				loaded[loaded.length] = {
					// recompose the url from previous object, #weird
					url: loaded.length > 0 ? loaded[loaded.length-1].url +'/'+ url : url
				  , module: module[0]
				  , index: loaded.length
				}
				loaded =  !!breadcrumb.length ? match( breadcrumb, module[1], loaded ) : loaded
			} else {
				loaded[loaded.length] = {
					url: '404'
				  , module: Todo
				  , index: loaded.length
				}
			}
			// This object refers all modules from url
			// Even those who may already be loaded
			return loaded
		}

	return {
		init: function(){
			When(request)
				.map(function(v){
					// Split full path in modules
					breadcrumb = v.search("/") < 0 ? [v] : v.split('/')
					// Clear extra loaded modules
					// but we keep the first ones in case it matches the url
					loaded = loaded.splice(breadcrumb.length, -1)
					// Check if modules are loaded, or retrieve module object
					return match(breadcrumb, Router)
				})
				.await( Module.update.then( Grid.update ).then( Header.update ) )
				.subscribe()
		}
	  , request: request
	}

})();


// MagicTrick()
$(function(){

	Grid.init()
	Header.init()
	Navigation.init()
	Raindrop.init()

	$("#wrapper").on("click", "article.list li, article.list dd", function(e){
		var el = $(this).closest("li, dd")
		window.location = el.find("a").attr("href")
		el.addClass("active").siblings().removeClass("active")
	})

})
