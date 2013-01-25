define(["./run","./logs"], function(Run, Logs){

	return {
		id: 'play',
		name: "Play monitor",
		icon: "▶",
		url: "#play",
		routes: {
			'play': function(bcs) {
				return $.map(bcs, function(crumb) {
					return {
						widget: Run
					};
				});
			}
		}
	};
});
