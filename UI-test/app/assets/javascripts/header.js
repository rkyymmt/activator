var Header = {
	init: function(){
		this.el = $("body > header nav")
	}

  , update: Action(function(modules, n){
		Header.el.empty()
		_.each(modules, function(i){
			$("<a/>")
				.html(i.data && i.data.name ? i.data.name : i.module.name)
				.attr("href", "#"+i.url)
				.appendTo(Header.el)
		})
		n(modules)
	})
}

