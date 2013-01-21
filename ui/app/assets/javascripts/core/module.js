define({
	// Load modules if not loaded
	load: Action(function(modules,n){
		var self = this
		// Find url from [v]
		var plugins = modules.map(function(i){
			return "plugins/"+ i.pluginID
		})
		// Load with require
		require(plugins, function(){
			plugins = [].slice.call(arguments)
			var y = 0
			// Map loaded modules with [v]
			n.call(self, modules.map(function(i){
				//i.plugin = plugins[y++];
				i.module =  i.module || new plugins[y++](i);
				// Todo, handle errors / undefined
				return i;
			}));
		})
	})
});
