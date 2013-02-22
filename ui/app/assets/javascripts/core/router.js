define(['./pluginapi'], function(api) {
	var ko = api.ko;

	// Decomposed url in an array
	var breadcrumbs = [];

	// New router API:
	//  Route format:
	// {
	//    'foo':    [ action, {
	//                   'bar': [ action ]
	//                   ':id': [ action ]
	//               }]
	//    'baz':  action,
	//    'crazy': {
	//      action: function(urlInfo) {},
	//      next: {
	//        ':id': function() {}
	//      },
	//      context: this
	//    }
	// }
	//
	//  Where action =
	//  a function that takes in these arguments:
	//  - urlInfo {
	//      name: 'bar',            // The current portion of the url
	//      full: ['foo', 'bar'],  // The decomposed breadcrumbs of the full url currently used.
	//      rest: [],              // An array of remaining url pieces.
	//      before: 'foo',         // The url string before this one (for back functionality).
	//      path: '',              // The full url path to this breadcrumb.
	//      isNew: true            // A flag representing if this is a new part of the URL or not.
	//    }
	//
	//  For any given URL that's hit, all the actions found leading down the tree are
	//  executed.

	// The set of routes we can use.
	var routes = {};

	// This is our default errorRoute.  Right now it just logs an error and displays an alert.
	var errorRoute = {
			action: function(args) {
				console.log('Failed to find router for ', args)
				alert("This is not a valid link: #" + args.path);
			}
	}

	// Create the 'rich' classes for breadcrumbs that are passed to router action handlers.
	// bcs - an array of url pieces
	// returns:
	//      an array of "Rich" URL pieces witht he following info:
	// {
	//   name -  The full text of this url piece.  So for the url /foo/bar/baz/bish, this represents on of foo, bar, baz or bish.
	//   full -  All the url pieces being routed.  So for the url /foo/bar/baz/bish, this would be ['foo', 'bar', 'baz', 'bish'].
	//   rest -  The url pieces after the current url piece. So for the url /foo/bar/baz/bish, when on the "baz" piece, this would be ['bish']
	//   before - The url path before the current piece.  So, for the url /foo/bar/baz/bish, when on the "baz" piece, this would be /foo/bar
	//   path  - The path to this url piece, so for url /foo/bar/baz/bish, this would be /foo/bar/baz when on the "baz" piece.
	// }
	function createArgs(bcs) {
		return $.map(bcs, function(bc, idx) {
			// Check to see if this guy is new to the URL and needs executed:
			return {
				name: bc,
				full: bcs,
				rest: bcs.slice(idx+1),
				before: bcs.slice(0, idx).join('/'),
				path: bcs.slice(0,idx+1).join('/'),
				// Tells us if this part of the URL is new.
				isNew: !((idx in breadcrumbs) && breadcrumbs[idx] == bc)
			};
		});
	}

	// This functions pickes the next route name to grab.
	// Keeps track of precednece of direct names vs. matching urls, like ":id" or ":all".
	function pickRouteName(urlPart, routes) {
		var name = urlPart.name;
		if(routes.hasOwnProperty(name)) {
			return name;
		}
		if(routes.hasOwnProperty(":id")) {
			return ":id";
		}
		return ":all";
	}
	// Executes the routing functions we need, based on the parsed url parts.
	var executeRoutes = function(routes, urlParts) {
		if(urlParts.length > 0) {
			var current = urlParts[0];
			var routeProp = pickRouteName(current, routes);
			var route = routes[current.name] || errorRoute;
			// Here - we unify all our data into the same format...
			// First, if it's a raw object, promote into route object.
			if(typeof(route) == 'function') {
				routes[current.name] = {
						action: route
				};
				route = routes[current.name];
			}
			// Here, if we have an array we unify into a route object.
			if(route.hasOwnProperty('length')) {
				routes[current.name] = {
						action: route[0],
						next: route[1]
				};
				route = routes[current.name];
			}
			// TODO - Is this context right?
			route.action.call(route.context || route, current);
			// See if we need to continue
			if(route.next) {
				executeRoutes(route.next, urlParts.slice(1));
			}
		}
	}

	// Parse a new # url and execute the actions associated with the route.
	// Note:  the url parameter is optional. If none is passed, this will pull the current window.location.hash.
	var parse = function(url) {
		// If no arguments, take the hash
		url = url || window.location.hash;
		// Split full path in modules
		var bcs = url ? /^#?\/?(.+)\/?$/.exec(url)[1].split("/") : [];
		// Make arguments to churn through routers...
		var args = createArgs(bcs);
		// TODO - Check if we're empty and add a link to the 'home' widget action?
		// Check if modules are loaded, or retrieve module object
		executeRoutes(routes, args);
		// Update the breadcrumbs so we remember what happened.
		breadcrumbs = bcs;
	};

	return {
		// Registers our initialization so that we drive the application from the hash.
		init: function() {
			// Register for future changes, and also parse immediately.
			$(window).on('hashchange', function() {
				parse(window.location.hash);
			});
			parse(window.location.hash);
		},
		// Register a plugin's routes.
		// TODO - do this recursive and *merge* routes.
		registerRoutes: function(newRoutes) {
			for(route in newRoutes) {
				if(newRoutes.hasOwnProperty(route)) {
					routes[route] = newRoutes[route];
				}
			}
		}
	};
});
