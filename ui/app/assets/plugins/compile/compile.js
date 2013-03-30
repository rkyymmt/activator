define(['text!./compile.html', 'core/pluginapi', 'core/log', 'css!./compile.css'], function(template, api, log){

	var ko = api.ko;
	var sbt = api.sbt;

	var Compile = api.Widget({
		id: 'compile-widget',
		template: template,
		init: function(parameters){
			var self = this

			this.title = ko.observable("Compile");
			this.activeTask = ko.observable(""); // empty string or taskId
			this.haveActiveTask = ko.computed(function() {
				return self.activeTask() != "";
			}, this);
			this.startStopLabel = ko.computed(function() {
				if (self.haveActiveTask())
					return "Stop";
				else
					return "Start";
			}, this);
			this.needCompile = ko.observable(false);
			this.recompileOnChange = ko.observable(true);

			this.logModel = new log.Log();

			this.status = ko.observable(api.STATUS_DEFAULT);

			api.events.subscribe(function(event) {
				return event.type == 'FilesChanged';
			},
			function(event) {
				if (self.recompileOnChange()) {
					console.log("files changed, doing a recompile");
					// doCompile just marks a compile pending if one is already
					// active.
					self.doCompile();
				} else {
					console.log("recompile on change unchecked, doing nothing");
				}
			});

			self.reloadSources(null);
		},
		update: function(parameters){
		},
		// after = optional
		reloadSources: function(after) {
			var self = this;

			self.logModel.info("Refreshing list of source files to watch for changes...");
			// Are we busy when watching sources? I think so...
			self.status(api.STATUS_BUSY);
			sbt.watchSources({
				onmessage: function(event) {
					console.log("event watching sources", event);
					self.logModel.event(event);
				},
				success: function(data) {
					console.log("watching sources result", data);
					self.status(api.STATUS_DEFAULT);
					self.logModel.info("Will watch " + data.count + " source files.");
					if (typeof(after) === 'function')
						after();
				},
				failure: function(status, message) {
					console.log("watching sources failed", message);
					self.logModel.warn("Failed to reload source file list: " + message);
					// WE should modify our status here!
					self.status(api.STATUS_ERROR);
					if (typeof(after) === 'function')
						after();
				}
			});
		},
		afterCompile: function(succeeded) {
			var self = this;

			if (succeeded)
				self.status(api.STATUS_DEFAULT);
			else
				self.status(api.STATUS_ERROR);

			if (self.needCompile()) {
				console.log("need to recompile because something changed while we were compiling");
				self.needCompile(false);
				self.doCompile();
			} else if (succeeded) {
				// asynchronously reload the list of sources in case
				// they changed. we are trying to serialize sbt usage
				// here so we only send our event out when we finish
				// with the reload.
				self.reloadSources(function() {
					// notify others
					api.events.send({ 'type' : 'CompileSucceeded' });
				});
			}
		},
		doCompile: function() {
			var self = this;
			if (self.haveActiveTask()) {
				console.log("Attempt to compile with a compile already active, will recompile again when we finish");
				self.needCompile(true);
				return;
			}

			self.status(api.STATUS_BUSY);
			self.logModel.clear();
			self.logModel.info("Compiling...");
			var task = { task: 'compile' };
			var taskId = sbt.runTask({
				task: task,
				onmessage: function(event) {
					if (self.logModel.event(event)) {
						// logged already
					} else {
						self.logModel.leftoverEvent(event);
					}
				},
				success: function(data) {
					console.log("compile result: ", data);
					self.activeTask("");
					if (data.type == 'GenericResponse') {
						self.logModel.info('Compile complete.');
					} else {
						self.logModel.error('Unexpected reply: ' + JSON.stringify(data));
					}
					self.afterCompile(true); // true=success
				},
				failure: function(status, message) {
					console.log("compile failed: ", status, message)
					self.activeTask("");
					self.logModel.error("Request failed: " + status + ": " + message);
					self.afterCompile(false); // false=failed
				}
			});
			self.activeTask(taskId);
		},
		startStopButtonClicked: function(self) {
			console.log("Start/stop compile was clicked");
			if (self.haveActiveTask()) {
				sbt.killTask({
					taskId: self.activeTask(),
					success: function(data) {
						console.log("kill success: ", data)
					},
					failure: function(status, message) {
						console.log("kill failed: ", status, message)
						self.logModel.error("Killing task failed: " + status + ": " + message)
					}
				});
			} else {
				self.doCompile();
			}
		}
	});

	var compileConsole = new Compile();

	return api.Plugin({
		id: 'compile',
		name: "Compile",
		icon: "B",
		url: "#compile",
		routes: {
			'compile': function() { api.setActiveWidget(compileConsole); }
		},
		widgets: [compileConsole],
		status: compileConsole.status
	});
});
