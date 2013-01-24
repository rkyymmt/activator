define(["./browse"], function(Browser){

	return {
    id: 'code',
		name: "Code",
		icon: "",
		url: "#code",
		routes: {
			'code': function(bcs) {
				return $.map(bcs, function(crumb) {
					return {
						widget: Browser
					};
				});
			}
		}
	};

});
