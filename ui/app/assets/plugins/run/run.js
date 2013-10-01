define(['core/model', 'text!./run.html', 'core/pluginapi', 'core/widgets/log', 'css!./run.css'], function(model, template, api, log, css){

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
			// note: we generally want to use the explicitly-set defaultMainClass even if it
			// isn't in the list of discovered mainClasses
			this.defaultMainClass = ko.observable("");
			this.currentMainClass = ko.observable("");
			this.haveMainClass = ko.computed(function() {
				// when we set mainClasses to empty list (as it is by default), knockout will set
				// currentMainClass to 'undefined'
				return typeof(self.currentMainClass()) == 'string' && self.currentMainClass() != "";
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
			this.runInConsole = ko.observable(false);
			this.restartPending = ko.observable(false);
			this.reloadMainClassPending = ko.observable(true);
			// last task ID we tried to stop
			this.stoppingTaskId = '';

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
			this.playAppLink = ko.observable('');
			this.playAppStarted = ko.computed(function() { return this.haveActiveTask() && this.playAppLink() != ''; }, this);
			this.atmosLink = ko.observable('');
			this.atmosCompatible = model.snap.app.hasConsole;
			this.runningWithAtmos = ko.computed(function() {
				return this.haveActiveTask() && this.atmosLink() != '' && model.snap.signedIn();
			}, this);
			this.runningWithoutAtmosButEnabled = ko.computed(function() {
				return this.haveActiveTask() && this.atmosLink() == '' && model.snap.signedIn() && this.runInConsole();
			}, this);
			this.runningWithoutAtmosBecauseDisabled = ko.computed(function() {
				return this.haveActiveTask() && this.atmosLink() == '' && model.snap.signedIn() && !this.runInConsole();
			}, this);
			this.notSignedIn = ko.computed(function() {
				return !model.snap.signedIn();
			}, this);
			this.notRunningAndSignedInAndAtmosEnabled = ko.computed(function() {
				return !this.haveActiveTask() && this.runInConsole() && model.snap.signedIn();
			}, this);
			this.notRunningAndSignedInAndAtmosDisabled = ko.computed(function() {
				return !this.haveActiveTask() && !this.runInConsole() && model.snap.signedIn();
			}, this);
			this.status = ko.observable('Application is stopped.');
		},
		update: function(parameters){
		},
		loadMainClasses: function(success, failure) {
			var self = this;

			// the spaghetti here is getting really, really bad.
			function taskCompleteShouldWeAbort() {
				if (!self.haveActiveTask())
					console.log("BUG should not call this without an active task");
				var weWereStopped = (self.activeTask() == self.stoppingTaskId);

				// clear out our task always
				self.activeTask('');

				if (self.restartPending()) {
					console.log("Need to start over due to restart");
					self.logModel.debug("Restarting...");
					self.restartPending(false);
					self.loadMainClasses(success, failure);
					// true = abort abort
					return true;
				} else if (weWereStopped) {
					console.log("Stopped, restart not requested");
					self.logModel.debug("Stopped");
					// true = abort abort
					return true;
				} else {
					// false = continue
					return false;
				}
			}

			self.logModel.debug("launching discoveredMainClasses task");
			var taskId = sbt.runTask({
				task: 'discovered-main-classes',
				onmessage: function(event) {
					console.log("event discovering main classes", event);
					self.logModel.event(event);
				},
				success: function(data) {
					console.log("discovered main classes result", data);

					if (taskCompleteShouldWeAbort())
						return;

					var names = [];
					if (data.type == 'GenericResponse') {
						names = data.params.names;
						self.logModel.debug("Discovered main classes: " + names);
					} else {
						self.logModel.debug("No main classes discovered");
					}
					self.logModel.debug("Got auto-discovered main classes, looking for a default mainClass setting if any");
					function noDefaultMainClassLogging(message) {
						if (names.length > 0) {
							self.logModel.debug("Didn't find a default mainClass setting, we'll just pick one of: " + names);
						} else {
							if (message)
								self.logModel.error(message);
							self.logModel.error("Didn't auto-discover a main class, and no mainClass was set");
						}
					}
					self.logModel.debug("launching mainClass task");
					var taskId = sbt.runTask({
						task: 'main-class',
						onmessage: function(event) {
							console.log("event getting default main class", event);
							self.logModel.event(event);
						},
						success: function(data) {
							console.log("default main class result", data);

							if (taskCompleteShouldWeAbort())
								return;

							var name = '';
							// 'name' won't be in here if mainClass was unset
							if (data.type == 'GenericResponse' && 'name' in data.params) {
								name = data.params.name;
								self.logModel.debug("Default main class is '" + name + "'");
							} else {
								// this isn't what really happens if it's not configured, I think
								// sbt just tries to ask the user to pick, which fails, and we
								// get the failure callback. But log just in case.
								noDefaultMainClassLogging();
							}
							success({ name: name, names: names });
						},
						failure: function(status, message) {
							// a common reason for fail is that sbt tried to ask /dev/null to
							// pick a main class manually.
							console.log("getting default main class failed", message);

							if (taskCompleteShouldWeAbort())
								return;

							noDefaultMainClassLogging();
							// we don't treat this as failure, just as no default set
							success({ name: '', names: names });
						}
					});
					self.activeTask(taskId);
				},
				failure: function(status, message) {
					console.log("getting main classes failed", message);

					if (taskCompleteShouldWeAbort())
						return;

					self.logModel.debug("Failed to discover main classes: " + message);
					failure(status, message);
				}
			});
			self.activeTask(taskId);
		},
		onCompileSucceeded: function(event) {
			var self = this;

			console.log("Compile succeeded - marking need to reload main class info");
			self.reloadMainClassPending(true);
			if (self.rerunOnBuild()) {
				console.log("Restarting due to completed compile");
				self.doRestart();
			} else {
				console.log("Run-on-compile not enabled, but we want to load main classes to fill in the option menu.");
				self.doMainClassLoadThenMaybeRun(false /* shouldWeRun */);
			}
		},
		beforeRun: function() {
			var self = this;
			if (self.reloadMainClassPending()) {
				self.logModel.info("Loading main class information...");
				self.status('Loading main class...');
			} else {
				self.status('Running...');
				self.logModel.info("Running...");
			}

			self.restartPending(false);
		},
		doRunWithMainClassLoad: function() {
			this.doMainClassLoadThenMaybeRun(true /* shouldWeRun */);
		},
		doMainClassLoadThenMaybeRun: function(shouldWeRun) {
			var self = this;

			// we clear logs here then ask doRunWithoutMainClassLoad not to.
			self.logModel.clear();

			self.beforeRun();

			// whether we get main classes or not we'll try to
			// run, but get the main classes first so we don't
			// fail if there are multiple main classes.
			function afterLoadMainClasses() {
				self.reloadMainClassPending(false);

				if (shouldWeRun) {
					self.logModel.debug("Done loading main classes - now running the project");
					self.doRunWithoutMainClassLoad(false /* clearLogs */);
				}
			}

			// update our list of main classes
			this.loadMainClasses(function(data) {
				// SUCCESS
				console.log("GOT main class info ", data);

				// hack because run-main doesn't work on Play right now.
				if (model.snap.app.hasPlay()) {
					console.log("OVERRIDING main class info due to Play app; dropping it all");
					data.name = '';
					data.names = [];
				}

				self.defaultMainClass(data.name);
				console.log("Set default main class to " + self.defaultMainClass());
				// ensure the default configured class is in the menu
				if (self.defaultMainClass() != '' && data.names.indexOf(self.defaultMainClass()) < 0)
					data.names.push(self.defaultMainClass());

				// when we set mainClasses, knockout will immediately also set currentMainClass to one of these
				// due to the data binding on the option menu.
				var actualCurrent = self.currentMainClass();
				if (typeof(actualCurrent) == 'undefined')
					actualCurrent = '';
				var newCurrent = '';

				console.log("Current main class was: '" + actualCurrent + "'");
				// so here's where knockout makes currentMainClass into something crazy
				self.mainClasses(data.names);
				console.log("Set main class options to " + self.mainClasses());

				// only force current selection to change if it's no longer
				// discovered AND no longer explicitly configured in the build.
				if (actualCurrent != '' && self.mainClasses().indexOf(actualCurrent) >= 0) {
					newCurrent = actualCurrent;
					console.log("Keeping current main class since it still exists: '" + newCurrent + "'");
				}

				// if no existing setting, try to set it
				if (newCurrent == '') {
					if (self.defaultMainClass() != '') {
						console.log("Setting current main class to the default " + self.defaultMainClass());
						newCurrent = self.defaultMainClass();
					} else if (self.mainClasses().length > 0) {
						console.log("Setting current main class to the first in our list");
						newCurrent = self.mainClasses()[0];
					} else {
						console.log("We have nothing to set the current main class to");
						newCurrent = '';
					}
				}

				console.log("Current main class is now: '" + newCurrent + "'");
				self.currentMainClass(newCurrent);

				afterLoadMainClasses();
			},
			function(status, message) {
				// FAIL
				console.log("FAILED to set up main classes");
				afterLoadMainClasses();
			});
		},
		doAfterRun: function() {
			var self = this;
			self.activeTask("");
			self.playAppLink("");
			self.atmosLink("");
			if (self.restartPending()) {
				self.doRun();
			}
		},
		doRunWithoutMainClassLoad: function(clearLogs) {
			var self = this;

			self.outputModel.clear();

			if (clearLogs)
				self.logModel.clear();

			self.beforeRun();

			var task = {};
			if (self.haveMainClass()) {
				task.task = 'run-main';
				task.params = { mainClass: self.currentMainClass() };
			} else {
				task.task = 'run';
			}

			if (self.runInConsole()) {
				task.task = 'atmos:' + task.task;
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
						self.playAppLink(url);
					} else if (event.id == 'atmosStarted') {
						self.atmosLink(event.params.uri);
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
		doRun: function() {
			if (this.reloadMainClassPending())
				this.doRunWithMainClassLoad();
			else
				this.doRunWithoutMainClassLoad(true /* clearLogs */);
		},
		doStop: function() {
			var self = this;
			if (self.haveActiveTask()) {
				self.stoppingTaskId = self.activeTask();
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
				self.doRun();
			}
		},
		doRestart: function() {
			if (this.haveActiveTask()) {
				this.doStop();
				this.restartPending(true);
			} else {
				this.doRun();
			}
		},
		restartButtonClicked: function(self) {
			console.log("Restart was clicked");
			self.doRestart();
		},
		onPreDeactivate: function() {
			this.logScroll = this.logModel.findScrollState();
			this.outputScroll = this.outputModel.findScrollState();
		},
		onPostActivate: function() {
			this.logModel.applyScrollState(this.logScroll);
			this.outputModel.applyScrollState(this.outputScroll);
		},
		restartWithAtmos: function(self) {
			this.runInConsole(true);
			this.doRestart();
		},
		restartWithoutAtmos: function(self) {
			this.runInConsole(false);
			this.doRestart();
		},
		enableAtmos: function(self) {
			this.runInConsole(true);
		},
		disableAtmos: function(self) {
			this.runInConsole(false);
		},
		showLogin: function(self) {
			$('#user').addClass("open");
		}
	});

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
