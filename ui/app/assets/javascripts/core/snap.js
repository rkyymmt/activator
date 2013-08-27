// Sort of MVC (Module, Grid, Router)
define(['./model', './plugin', './router', './pluginapi', './navigation', './tutorial/tutorial', './streams'], function(model, plugins, router, api, navigation, Tutorial, streams) {

	var ko = api.ko;

	// Register webSocket error handler
	streams.subscribe({
		handler: function(event) {
			alert("Connection lost; you will need to reload the page or restart Activator. It's also possible that Activator is open in another tab, which causes this error.");
		},
		filter: function(event) {
			return event.type == streams.WEB_SOCKET_CLOSED;
		}
	});

	// Model for the whole app view
	$.extend(model, {
		plugins: plugins,
		router: router,
		// This is the initialization of the application...
		init: function() {
			var self = this;
			self.widgets = [];
			// TODO - initialize plugins in a better way perhaps...
			$.each(self.plugins.list, function(idx,plugin) {
				self.router.registerRoutes(plugin.routes);
				$.each(plugin.widgets, function(idx, widget) {
					self.widgets.push(widget);
				});
			});
			self.router.init();
			ko.applyBindings(self, window.body);
			navigation.init();
			// Fill out sbt project inspection data.
			api.sbt.runTask({
				task: 'name',
				success: function(result) {
					model.snap.app.hasAkka(result.params.hasAkka);
					model.snap.app.hasPlay(result.params.hasPlay);
					model.snap.app.hasConsole(result.params.hasConsole);
				}
			})
		},
		api: api,
		tutorial: new Tutorial()
	});
	model.init();

	var receiveMessage = function(event) {
		if (event.origin !== "https://typesafe.com") { // TODO change to typesafe.com
			console.log("receiveMessage: Ignoring message ", event);
		} else {
			var obj = JSON.parse(event.data);
			if ('signedIn' in obj && typeof(obj.signedIn) == 'boolean') {
				console.log("receiveMessage: signedIn=", obj.signedIn);
				model.snap.signedIn(obj.signedIn);
			} else {
				console.log("receiveMessage: did not understand message ", event, " parsed ", obj);
			}
		}
	}

	window.addEventListener("message", receiveMessage, false);

	// there is a race if the child iframe sent the message before we added that listener.
	// so we ask the iframe to resend. If the iframe is not started up yet, then this
	// won't do anything but the iframe will send automatically when it starts up.
	$('#loginIFrame').get(0).contentWindow.postMessage('{ "pleaseResendSignedIn" : true }', '*');

	return model;
});
