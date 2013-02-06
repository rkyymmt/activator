define(['text!./run.html'], function(template){

	var ko = req('vendors/knockout-2.2.1.debug');

	var Run = Widget({
		id: 'play-run-widget',
		template: template,
		init: function(parameters){
			this.title = ko.observable("Run");
			this.logs = ko.observableArray();
		},
		update: function(parameters){
		},
		runButtonClicked: function(self) {
			console.log("Run was clicked");
			var runRequest = {
				appId: serverAppModel.id,
				description: "Run",
				task: {
					type: "RunRequest"
				}
			};
			self.logs.push("Running...\n");
			$.ajax({
				url: '/api/sbt/task',
				type: 'POST',
				dataType: 'json', // return type
				contentType: 'application/json; charset=utf-8',
				data: JSON.stringify(runRequest),
				success: function(data) {
					console.log("run result: ", data);
					$.each(data.logs, function(i, value) {
						var message = value.message;
						var logType = value.type;
						self.logs.push(logType + ": " + message);
					})
					if (data.type == 'ErrorResponse') {
						self.logs.push(data.error);
					} else if (data.type == 'RunResponse') {
						self.logs.push('Run complete.');
					} else {
						self.logs.push('Unexpected reply: ' + JSON.stringify(data));
					}
				},
				failure: function(message) {
					console.log("run failed: ", message)
					self.logs.push("HTTP request failed: " + message);
				}
			});
		}
	});

	return {
		id: 'run',
		name: "Run",
		icon: "▶",
		url: "#run",
		routes: {
			'run': function(bcs) {
				return $.map(bcs, function(crumb) {
					return {
						widget: Run
					};
				});
			}
		}
	};
});
