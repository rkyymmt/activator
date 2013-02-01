define(function() {
    var ko = req('vendors/knockout-2.2.1.debug');

    // Decomposed url in an array
    var breadcrumb = [];

    // Routes are nested:
    // " /projects/:id/code/ " would call each related route
    // And each route is associated with a requirejs plugin
    // Example:
    //  {
    //      'foo':                  [ WidgetClass , {
    //          'bar':              [ WidgetClass , ":rest" ],
    //          ':id':              [ WidgetClass ]
    //      }],
    //      'baz':          function(urlFragments) {
    //                          return makeBreadCrumbFragments(urlFragments);
    //                      }
    //  }
    // Note
    // :rest will redirect all /foo/bar/.* urls.
    // 
    // WidgetClass is assumed to be a function that can be "newed" to create a renderable widget.
    // A Widget class needs the following:
    // constructor(args) - Where Args is a set of JSON representing the current breadcrumb, with the following format:
    // {
    //   args: {
    //      url: 'bar',            // The current portion of the url
    //      full: ['foo', 'bar'],  // The decomposed breadcrumbs of the full url currently used.
    //      rest: [],              // An array of remaining breadcrumbs, used after this widget.
    //      before: 'foo',         // The url string before this one (for back functionality).
    //      path: '',              // The full url path to this breadcrumb.
    //   },
    //   index: 2                  // The index relative to other breadcrumbs
    //   url: 'foo/bar'            // The *full* url to current breadcrumb.
    // }
    //
    // this.title =  A widget class must support a title property (either a function that returns one, or a direct attribute).
    // this.render ???  - The rendering of the widget....
    var routes = {};
    // TODO - Put this somewhere else or configure it?
    var ErrorWidget = Widget({
    		id: 'Error widget',
    		title: 'Not found!',
    		template: 'Url not found!'
    });

    // Create the args setting for breadcrumbs.
    // Args: bc - The current set of breadcrumbs.
    //       modules - The list of 'resolved configuration' for the route.

    function makeArgs(bc, modules) {
        var i = modules.length;
        var url = bc[0];
        return {
            url: url,
            full: breadcrumb,
            rest: bc.slice(1),
            before: i > 0 ? modules[i - 1].url : '',
            path: i > 0 ? modules[i - 1].args.path + '/' + url : url
        };
    }
    // Adds a new configuration to the list of modules.
    // Args: config  - The configuration for the current breadcrumb
    //       bc      - The remain url breadcrumbs
    //       modules - The 'resolved' breadcrumb configuration.

    function addBc(config, bc, modules) {
        config.args = makeArgs(bc, modules);
        config.url = (config.args.before ? config.args.before + "/" : "") + config.args.url;
        config.index = modules.length;
        modules[modules.length] = config;
    }
    // Returns the *key* used to find the next router.
    // args:  routes - all possible routes (in an object, by URL)
    //        url    - The current url to route.  

    function lookUpNextRouter(routes, url) {
        // Hierarchy of how we look up routes
        if(routes[url]) {
            return url;
        }
        if(routes[":id"]) {
            return ":id";
        }
        if(routes[":rest"]) {
            return ":rest";
        }
        // TODO - Special handling?
        return "404";
    }
    // This is a helper function used when
    // compiling routes into functions.
    // args:  routes - The current router configuration
    //        bc     - The bread crumbs to route
    var doRoute = function(router, bc) {
            // If the router *is* a function, just use that to construct BC configs,
            // Otherwise, assume we need to construct some routing function.
            if(typeof(router) == 'function') {
                return router(bc);
            }
            var rest = bc.slice(1);
            var url = bc[0];
            var result = [{
                widget: router[0]
            }];
            if(router[1] == ":rest") {
                return result;
            }
            if(router[1] && bc.length) {
                var routes = router[1];
                var route = lookUpNextRouter(routes, url);
                // compile the route.
                if(routes[route] && (typeof(routes[route]) != 'function')) {
                    routes[route] = compileRoute(routes[route]);
                }
                if(routes[route]) {
                    result.push.apply(result, routes[route](rest));
                } else {
                    result.push({
                        widget: ErrorWidget
                    });
                }
            }
            return result;
        }
        // Compilers a router configuration into a function
        // that returns breadcrumb configuration arrays.

    function compileRoute(router) {
        return function(bc) {
            return doRoute(router, bc);
        };
    }

    // From the breadcrumb, checks route syntax and get module (breadcrumb configuration arrays).
    var match = function(bc, routes, modules) {
            var url = bc[0];
            var routesKey = lookUpNextRouter(routes, url);

            if(routes[routesKey] && (typeof(routes[routesKey]) != 'function')) {
                // compile the route.
                routes[routesKey] = compileRoute(routes[routesKey]);
            }
            if(routes[routesKey]) {
                // Add arguments to the route?
                $.each(routes[routesKey](bc), function(idx, config) {
                    addBc(config, bc.slice(idx), modules);
                });
            } else {
                addBc({
                    widget: ErrorWidget
                }, ['404'], modules);
            }
            return modules;
        }

    var parsedBreadcrumbs = ko.observableArray();

    // Find the common prefix *length* for two arrays, using the given equals comparator.

    function commonPrefixIdx(one, two, comparator) {
        for(var idx = 0; idx < one.length && idx < two.length; idx++) {
            if(!comparator(one[idx], two[idx])) {
                return idx;
            }
        }
        return(one.length > two.length) ? two.length : one.length;
    }
    // merges the new breadcrumbs with the existing breadcrumbs, preserving the models already there if possible.
    // Note:  This will instantiate the widgets of new breadcrumbs.

    function mergeBreadCrumbs(newCrumbs) {
        var existing = parsedBreadcrumbs();
        var sameIdx = commonPrefixIdx(existing, newCrumbs, function(lhs, rhs) {
            return lhs.url == rhs.url;
        });
        // Remove stale crumbs
        var removeCount = existing.length - sameIdx;
        for(var i = 0; i < removeCount; i++) {
            parsedBreadcrumbs.pop();
        }
        // Grab the uninitialized crumbs and initialize them.
        // Also add them to the chain of stuff
        var uninitailizedCrumbs = newCrumbs.slice(sameIdx);
        $.each(uninitailizedCrumbs, function(idx, bc) {
            var widget = bc.widget;
            // Wait to instantiate widgets until we have to...
            bc.module = new widget(bc);
            parsedBreadcrumbs.push(bc);
        });
    }

    var parse = function(url) {
            // If no arguments, take the hash
            url = url || window.location.hash;
            // Split full path in modules
            breadcrumb = url ? /^#?\/?(.+)\/?$/.exec(url)[1].split("/") : [];
            // Check if modules are loaded, or retrieve module object
            loaded = match(breadcrumb.slice(0), routes, []);
            // Merge breadcrumbs with those already there.
            mergeBreadCrumbs(loaded);
        };
    return {
        init: function() {
            // Register for future changes, and also parse immediately.
            $(window).on('hashchange', function() {
                parse(window.location.hash);
            });
            parse(window.location.hash);
        },
        // Register a plugin's routes
        registerRoutes: function(newRoutes) {
            for(route in newRoutes) {
                if(newRoutes.hasOwnProperty(route)) {
                    routes[route] = newRoutes[route];
                }
            }
        },
        breadcrumbs: parsedBreadcrumbs
    };
});