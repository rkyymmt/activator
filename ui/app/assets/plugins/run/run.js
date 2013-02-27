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
			var self = this

			this.title = ko.observable("Run");
			this.logs = ko.observableArray();
			this.output = ko.observableArray();
			this.activeTask = ko.observable(""); // empty string or taskId
			this.mainClasses = ko.observableArray();
			this.currentMainClass = ko.observable("");
			this.haveMainClass = ko.computed(function() {
				return self.mainClasses().length > 0;
			}, this);
			this.haveActiveTask = ko.computed(function() {
				return self.activeTask() != "";
			}, this);

			// TODO we need to re-run this on changes (whenever we recompile)
			sbt.runTask({
				task: 'DiscoveredMainClassesRequest',
				onmessage: function(event) {
					console.log("event getting main class", event);
				},
				success: function(data) {
					console.log("main class result", data);
					if (data.type == 'DiscoveredMainClassesResponse') {
						self.mainClasses(data.names);
					} else {
						self.mainClasses([]);
					}
					// only force current selection to change if it's no longer
					// valid.
					if (self.mainClasses().indexOf(self.currentMainClass()) < 0)
						self.currentMainClass("");
					if (self.haveMainClass()) {
						// if no current one, set it
						if (self.currentMainClass() == "")
							self.currentMainClass(self.mainClasses()[0]);
					}
				},
				failure: function(message) {
					console.log("getting main class failed", message);
				}
			});
		},
		update: function(parameters){
		},
		startButtonClicked: function(self) {
			console.log("Run was clicked");
			self.logs.removeAll();
			self.output.removeAll();
			self.logs.push("Running...\n");
			var task = { task: 'RunRequest' };
			if (self.haveMainClass())
				task.params = { mainClass: self.currentMainClass() };
			var taskId = sbt.runTask({
				task: task,
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
					self.activeTask("");
					if (data.type == 'ErrorResponse') {
						self.logs.push(data.error);
					} else if (data.type == 'RunResponse') {
						self.logs.push('Run complete.');
					} else {
						self.logs.push('Unexpected reply: ' + JSON.stringify(data));
					}
				},
				failure: function(xhr, status, message) {
					console.log("run failed: ", status, message)
					self.activeTask("");
					self.logs.push("HTTP request failed: " + message);
				}
			});
			self.activeTask(taskId);
		},
		stopButtonClicked: function(self) {
			if (self.haveActiveTask()) {
				sbt.killTask({
					taskId: self.activeTask(),
					success: function(data) {
						console.log("kill success: ", data)
					},
					failure: function(xhr, status, message) {
						console.log("kill failed: ", status, message)
						self.logs.push("HTTP request to kill task failed: " + message)
					}
				});
			}
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
