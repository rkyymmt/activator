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
			this.output = ko.observableArray();
		},
		update: function(parameters){
		},
		runButtonClicked: function(self) {
			console.log("Run was clicked");
			self.logs.removeAll();
			self.output.removeAll();
			self.logs.push("Running...\n");
			sbt.runTask({
				task: 'RunRequest',
				onmessage: function(event) {
					if ('type' in event && event.type == 'LogEvent') {
						var message = event.entry.message;
						var logType = event.entry.type;
						if (logType == 'stdout' || logType == 'stderr') {
							self.output.push(stripAnsiCodes(message))
						} else if (logType == 'message') {
							self.logs.push(event.entry.level + ": " + stripAnsiCodes(message));
						} else {
							self.logs.push(logType + ": " + stripAnsiCodes(message));
						}
					} else if ('type' in event && event.type == 'Started') {
						// our request went to a fresh sbt, and we witnessed its startup.
						// we may not get this event if an sbt was recycled.
						// we move "output" to "logs" because the output is probably
						// just sbt startup messages that were not redirected.
						ko.utils.arrayPushAll(self.logs, self.output.removeAll());
						self.logs.valueHasMutated();
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

	var runConsole = new Run();

	return api.Plugin({
		id: 'run',
		name: "Run",
		icon: "▶",
		url: "#run",
		routes: {
			'run': function() { api.setActiveWidget(runConsole); }
		},
		widgets: [runConsole]
	});
});
