define(['css!./demo'], function(css, template){

	// var loadedUrls = {};

	// Model(s)

	// Fetch
	function fetch(parameters){
		// Do some checking on url, or not
		var url = parameters.url;
		// Get JSON
		return $.getJSON(url)
			.done(function(){
				// loadedUrls[parameters.url] = datas
			})
	}

	// Render
		// Creates a new view each times it's called
		// Bind UI events
	function render(parameters){
		// jquery object, DOM object or plain text html
    	return "<article><header><h1>Todo</h1></header><footer><p></p></footer><section class=\"wrapper\"></section></article>"
	}

	return {
		name: "DEMO",
		id: "demo",
		render: render
	}
})
