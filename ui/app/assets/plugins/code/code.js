define(["./browse", "./view"], function(Browser, Viewer){

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
			},
      'view': function(bcs) {
        var file = bcs.slice(1).join('/')
        return [{
          widget: Viewer,
          file: file
        }];
      }
		}
	};

});
