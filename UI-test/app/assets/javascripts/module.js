var Module = (function(){

	return {
		update: Action(function(v,n){
			var y = 1
			_.each(v, function(module){
				if ( !module.data && module.url && module.module.get ){
					y++
					module.module.get(module, function(data){
						module.data = data
						if (--y == 0) n(v)
					})
				}
			})
			if (--y == 0) n(v)
		})
	}

})();

