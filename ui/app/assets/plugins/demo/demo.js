define(["./browser"], function(Browser){

	return {
		name: "Demo",
		icon: "",
		url: "#demo",
		routes: {
			'demo': [ Browser, ":rest" ]
		}
	};

});
