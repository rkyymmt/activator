define(["./browse"], function(Browser){

	return {
    id: 'code',
		name: "Code",
		icon: "",
		url: "#code",
		routes: {
			'code':			[ Browser, ":rest"]
		}
	};

});
