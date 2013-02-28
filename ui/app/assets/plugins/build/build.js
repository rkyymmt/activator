define(['text!./build.html', 'core/pluginapi'], function(template, api, streams){

	var ko = api.ko;
	var sbt = api.sbt;

	// if we wanted to be cute we'd convert these to HTML tags perhaps
	var ansiCodeRegex = new RegExp("\\033\\[[0-9;]+m", "g");
	var stripAnsiCodes = function(s) {
		return s.replace(ansiCodeRegex, "");
	}

	var Build = api.Widget({
		id: 'build-widget',
		template: template,
		init: function(parameters){
			var self = this

			this.title = ko.observable("Build");
			this.logs = ko.observableArray();
			this.activeTask = ko.observable(""); // empty string or taskId
			this.haveActiveTask = ko.computed(function() {
				return self.activeTask() != "";
			}, this);
			this.needCompile = ko.observable(false);
			this.rebuildOnChange = ko.observable(true);

			api.events.subscribe(function(event) {
				return event.type == 'FilesChanged';
			},
			function(event) {
				if (self.rebuildOnChange()) {
					console.log("files changed, doing a rebuild");
					// doCompile just marks a compile pending if one is already
					// active.
					self.doCompile();
				} else {
					console.log("rebuild on change unchecked, doing nothing");
				}
			});

			// FIXME we have to re-do this whenever the build changes
			// or files are added/removed.
			sbt.watchSources({
				onmessage: function(event) {
					console.log("event watching sources", event);
				},
				success: function(data) {
					console.log("watching sources result", data);
				},
				failure: function(xhr, status, message) {
					console.log("watching sources failed", message);
				}
			});
		},
		update: function(parameters){
		},
		afterCompile: function(succeeded) {
			var self = this;

			if (self.needCompile()) {
				console.log("need to rebuild because something changed while we were compiling");
				self.needCompile(false);
				self.doCompile();
			} else if (succeeded) {
				api.events.send({ 'type' : 'CompileSucceeded' });
			}
		},
		doCompile: function() {
			var self = this;

			if (self.haveActiveTask()) {
				console.log("Attempt to compile with a compile already active, will rebuild again when we finish");
				self.needCompile(true);
				return;
			}

			self.logs.removeAll();
			self.logs.push("Building...\n");
			var task = { task: 'CompileRequest' };
			var taskId = sbt.runTask({
				task: task,
				onmessage: function(event) {
					if ('type' in event && event.type == 'LogEvent') {
						var message = event.entry.message;
						var logType = event.entry.type;
						if (logType == 'stdout' || logType == 'stderr') {
							self.logs.push(stripAnsiCodes(message))
						} else if (logType == 'message') {
							self.logs.push(event.entry.level + ": " + stripAnsiCodes(message));
						} else {
							self.logs.push(logType + ": " + stripAnsiCodes(message));
						}
					} else {
						self.logs.push("unknown event: " + JSON.stringify(event))
					}
				},
				success: function(data) {
					console.log("compile result: ", data);
					self.activeTask("");
					if (data.type == 'ErrorResponse') {
						self.logs.push(data.error);
					} else if (data.type == 'CompileResponse') {
						self.logs.push('Compile complete.');
					} else {
						self.logs.push('Unexpected reply: ' + JSON.stringify(data));
					}
					self.afterCompile(true); // true=success
				},
				failure: function(xhr, status, message) {
					console.log("compile failed: ", status, message)
					self.activeTask("");
					self.logs.push("HTTP request failed: " + message);
					self.afterCompile(false); // false=failed
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
		},
		startButtonClicked: function(self) {
			console.log("Start build was clicked");
			self.doCompile();
		}
	});

	var buildConsole = new Build();

	return api.Plugin({
		id: 'build',
		name: "Build",
		icon: "B",
		url: "#build",
		routes: {
			'build': function() { api.setActiveWidget(buildConsole); }
		},
		widgets: [buildConsole]
	});
});
