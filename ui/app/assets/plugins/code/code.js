define(['css!./code','text!./home.html'], function(css, template){

	// Render
	function render(parameters){
		var view = $(template);
		return view
	}

	return {
		name: "Code",
		id: "code",
		render: render
	}
})

