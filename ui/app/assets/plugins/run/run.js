define(['text!./run.html', 'core/pluginapi', 'core/widgets/log', 'css!./run.css'], function(template, api, log, css){

	var ko = api.ko;
	var sbt = api.sbt;

	var runConsole = api.PluginWidget({
		id: 'play-run-widget',
		template: template,
		init: function(parameters){
			var self = this

			this.title = ko.observable("Run");
			this.activeTask = ko.observable(""); // empty string or taskId
			this.mainClasses = ko.observableArray();
			this.currentMainClass = ko.observable("");
			this.haveMainClass = ko.computed(function() {
				return self.mainClasses().length > 0;
			}, this);
			this.haveActiveTask = ko.computed(function() {
				return self.activeTask() != "";
			}, this);
			this.startStopLabel = ko.computed(function() {
				if (self.haveActiveTask())
					return "Stop";
				else
					return "Start";
			}, this);
			this.rerunOnBuild = ko.observable(true);
			this.restartPending = ko.observable(false);

			api.events.subscribe(function(event) {
				return event.type == 'CompileSucceeded';
			},
			function(event) {
				self.onCompileSucceeded(event);
			});

			this.logModel = new log.Log();
			this.logScroll = this.logModel.findScrollState();
			this.outputModel = new log.Log();
			this.outputScroll = this.outputModel.findScrollState();
			this.status = ko.observable('Application is stopped.');
		},
		update: function(parameters){
		},
		onCompileSucceeded: function(event) {
			var self = this;

			// whether we get main classes or not we'll try to
			// run, but get the main classes first so we don't
			// fail if there are multiple main classes.
			function afterLoadMainClasses() {
				if (self.rerunOnBuild() && !self.haveActiveTask()) {
					self.doRun(true); // true=triggeredByBuild
				}
			}

			// update our list of main classes
			sbt.runTask({
				task: 'discovered-main-classes',
				onmessage: function(event) {
					console.log("event getting main class", event);
				},
				success: function(data) {
					console.log("main class result", data);
					if (data.type == 'GenericResponse') {
						self.mainClasses(data.params.names);
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
					afterLoadMainClasses();
				},
				failure: function(status, message) {
					console.log("getting main class failed", message);
					afterLoadMainClasses();
				}
			});
		},
		doAfterRun: function() {
			var self = this;
			self.activeTask("");
			if (self.restartPending()) {
				self.doRun(false); // false=!triggeredByBuild
			}
		},
		doRun: function(triggeredByBuild) {
			var self = this;

			self.logModel.clear();
			self.outputModel.clear();

			if (triggeredByBuild) {
				self.logModel.info("Build succeeded, running...");
				self.status('Build succeeded, running...');
			} else if (self.restartPending()) {
				self.status('Restarting...');
				self.logModel.info("Restarting...");
			} else {
				self.status('Running...');
				self.logModel.info("Running...");
			}

			self.restartPending(false);

			var task = null;
			if (self.haveMainClass()) {
				task = { task: 'run-main', params: { mainClass: self.currentMainClass() } };
			} else {
				task = { task: 'run' }
			}
			var taskId = sbt.runTask({
				task: task,
				onmessage: function(event) {
					if (event.type == 'LogEvent') {
						var logType = event.entry.type;
						if (logType == 'stdout' || logType == 'stderr') {
							self.outputModel.event(event);
						} else {
							self.logModel.event(event);
						}
					} else if (event.type == 'Started') {
						// our request went to a fresh sbt, and we witnessed its startup.
						// we may not get this event if an sbt was recycled.
						// we move "output" to "logs" because the output is probably
						// just sbt startup messages that were not redirected.
						self.logModel.moveFrom(self.outputModel);
					} else if (event.id == 'playServerStarted') {
						var port = event.params.port;
						var url = 'http://localhost:' + port;
						var link = '<a href="'+url+'" target="_blank">' + url + '</a>';
						self.status(link + ' - Play application started.');
					} else {
						self.logModel.leftoverEvent(event);
					}
				},
				success: function(data) {
					console.log("run result: ", data);
					if (data.type == 'GenericResponse') {
						self.logModel.info('Run complete.');
						self.status('Run complete');
					} else {
						self.logModel.error('Unexpected reply: ' + JSON.stringify(data));
					}
					self.doAfterRun();
				},
				failure: function(status, message) {
					console.log("run failed: ", status, message)
					self.status('Run failed');
					self.logModel.error("Failed: " + status + ": " + message);
					self.doAfterRun();
				}
			});
			self.activeTask(taskId);
		},
		doStop: function() {
			var self = this;
			if (self.haveActiveTask()) {
				sbt.killTask({
					taskId: self.activeTask(),
					success: function(data) {
						console.log("kill success: ", data)
					},
					failure: function(status, message) {
						console.log("kill failed: ", status, message)
						self.status('Unable to stop');
						self.logModel.error("HTTP request to kill task failed: " + message)
					}
				});
			}
		},
		startStopButtonClicked: function(self) {
			console.log("Start or Stop was clicked");
			if (self.haveActiveTask()) {
				// stop
				self.restartPending(false);
				self.doStop();
			} else {
				// start
				self.doRun(false); // false=!triggeredByBuild
			}
		},
		restartButtonClicked: function(self) {
			console.log("Restart was clicked");
			self.doStop();
			self.restartPending(true);
		},
		onPreDeactivate: function() {
			this.logScroll = this.logModel.findScrollState();
			this.outputScroll = this.outputModel.findScrollState();
		},
		onPostActivate: function() {
			this.logModel.applyScrollState(this.logScroll);
			this.outputModel.applyScrollState(this.outputScroll);
		}
	});

	return new api.Plugin({
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
