define(function(){

	var Error = Class({
		title: "Error: 404",
		init: noop,
		render: function(){
			return "<article><header><h1>Not found</h1></header><footer><p></p></footer><section class=\"wrapper\"></section></article>"
		}
	});

	return Error;

});
