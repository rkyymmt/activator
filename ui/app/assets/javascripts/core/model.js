define(['./pluginapi'], function(api) {

	var ko = api.ko;

	// Model for the whole app view; created in two parts
	// so that this first part is available during construction
	// of the second part.
	var model = {
		snap: {
			// TODO - This should be observable and we get notified of changes by sbt....
			appName: window.serverAppModel.name ? window.serverAppModel.name : window.serverAppModel.id,
			pageTitle: ko.observable(),
			activeWidget: api.activeWidget,
			// TODO load last value from somewhere until we get a message from the iframe
			signedIn: ko.observable(false)
		}
	};

	return model;
});
