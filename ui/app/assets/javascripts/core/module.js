var Module = {
	// Load plugins if not loaded
	update: Action(function(modules,n){
		var self = this
		// Find url from [v]
		var plugins = modules.map(function(i){
			return "plugins/"+ i.plugin
		})
		// Load with require
		require(plugins, function(){
			plugins = [].slice.call(arguments)
			var y = 0
			// Map loaded modules with [v]
			n.call(self, modules.map(function(i){
				i.plugin = plugins[y++]
				// Todo, handle errors / undefined
				return i
			}))
		})
	})
}
