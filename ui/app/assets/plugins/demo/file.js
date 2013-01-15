define(['css!./demo'], function(css, template){

	// Render
	function render(parameters){
		var view = $("<article><header><h1>File</h1></header><footer><p></p></footer><section class=\"wrapper\"></section></article>");
		return view
	}

	return {
		name: "FILE",
		id: "file",
		render: render
	}
})

