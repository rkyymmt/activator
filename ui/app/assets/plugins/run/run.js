define(['text!./run.html', 'core/pluginapi'], function(template, api){

	var ko = api.ko;
	var sbt = api.sbt;

	// if we wanted to be cute we'd convert these to HTML tags perhaps
	var ansiCodeRegex = new RegExp("\\033\\[[0-9;]+m", "g");
	var stripAnsiCodes = function(s) {
		return s.replace(ansiCodeRegex, "");
	}

	var Run = api.Widget({
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
			self.logs.push("Running...\n");
			sbt.runTask({
				task: 'RunRequest',
				onmessage: function(event) {
					if ('type' in event && event.type == 'LogEvent') {
						var message = event.entry.message;
						var logType = event.entry.type;
						self.logs.push(logType + ": " + stripAnsiCodes(message));
					} else {
						self.logs.push("unknown event: " + JSON.stringify(event))
					}					
				},
				success: function(data) {
					console.log("run result: ", data);
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
			'run': [Run]
		}
	};
});
