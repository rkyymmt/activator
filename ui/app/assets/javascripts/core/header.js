var Header = {
	init: function(){
		this.el = $("body > header nav")
	},

	update: Action(function(modules, n){
		Header.el.empty()
		$.each(modules, function(ø,i){
			$("<a/>")
				.html(i.data && i.data.name ? i.data.name : i.plugin.name)
				.attr("href", "#"+i.url)
				.appendTo(Header.el)
		})
		n(modules)
	})
}

