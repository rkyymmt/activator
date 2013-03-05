define(['text!./test.html', 'css!./test.css', 'core/pluginapi', 'core/log'], function(template, css, api, log) {
	var ko = api.ko;
	var sbt = api.sbt;

	var Outcome = {
		PASSED: 'passed',
		FAILED: 'failed',
		ERROR: 'error',
		SKIPPED: 'skipped',
		// PENDING doesn't arrive from server, it's just a state we use locally
		PENDING: 'pending'
	};

	// TODO - Other widgety things here.
	var TestResult = api.Class({
		init: function(config) {
			var self = this;
			self.name = config.name;
			self.outcome = ko.observable(config.outcome);
			self.description = ko.observable(config.description);
			self.outcomeClass = ko.computed(function() {
				var current = self.outcome();
				if (current === Outcome.PASSED || current === Outcome.PENDING) {
					return current;
				} else {
					return Outcome.FAILED;
				}
			});
		},
		// Update our state from an event.
		update: function(event) {
			this.description(event.description);
			this.outcome(event.outcome);
		}
	});
	var TestDisplay = api.Widget({
		id: 'test-result-widget',
		title: 'Testing',
		template: template,
		init: function(parameters) {
			var self = this;
			self.results = ko.observableArray();
			self.testStatus = ko.observable('Waiting to test');
			self.logModel = new log.Log();
			// TODO - Store state beyond the scope of this widget!
			// We should probably be listening to tests *always*
			// and displaying latest status *always*.
			self.hasResults = ko.computed(function() {
				return self.results().length > 0;
			});
			self.testFilter = ko.observable('all');
			self.filterTestsText = ko.computed(function() {
				if(self.testFilter() == 'all') {
					return 'Show only failures';
				}
				return 'Show all tests';
			});
			self.displayedResults = ko.computed(function() {
				if(self.testFilter() == 'failures') {
					return ko.utils.arrayFilter(self.results(), function(item) {
						return item.outcome() != Outcome.PASSED;
					});
				}
				return self.results();
			});
			// Rollup results.
			self.resultStats = ko.computed(function() {
				var results = {
						passed: 0,
						failed: 0
				};
				$.each(self.results(), function(idx, result) {
					if(result.outcome() != Outcome.PASSED) {
						results.failed = results.failed + 1;
					} else {
						results.passed = results.passed + 1;
					}
				});
				return results;
			});

			this.activeTask = ko.observable(""); // empty string or taskId
			this.haveActiveTask = ko.computed(function() {
				return self.activeTask() != "";
			}, this);
			this.rerunOnBuild = ko.observable(false);
			this.restartPending = ko.observable(false);

			api.events.subscribe(function(event) {
				return event.type == 'CompileSucceeded';
			},
			function(event) {
				self.onCompileSucceeded(event);
			});
		},
		filterTests: function() {
			// TODO - More states.
			if(this.testFilter() == 'all') {
				this.testFilter('failures')
			} else {
				this.testFilter('all')
			}
		},
		doAfterTest: function() {
			var self = this;
			self.activeTask("");
			if (self.restartPending()) {
				self.doTest(false); // false=!triggeredByBuild
			}
		},
		doTest: function(triggeredByBuild) {
			var self = this;

			self.logModel.clear();
			self.testStatus('Running tests...')

			if (triggeredByBuild) {
				self.logModel.info("Build succeeded, testing...");
			} else if (self.restartPending()) {
				self.logModel.info("Restarting...");
			} else {
				self.logModel.info("Testing...");
			}

			self.restartPending(false);

			// TODO - Do we want to clear the test data we had previously
			// or append?  Tests may disappear and we'd never know...

			var taskId = sbt.runTask({
				task: 'TestRequest',
				onmessage: function(event) {
					if (self.logModel.event(event)) {
						// nothing
					} else if (event.type == 'TestEvent') {
						self.updateTest(event);
					} else if (event.type == 'Started') {
						// this is expected when we start a new sbt, but we don't do anything with it
					} else {
						self.logModel.warn("unknown event: " + JSON.stringify(event))
					}
				},
				success: function(data) {
					console.log("test result: ", data);

					if (data.type == 'ErrorResponse') {
						self.logModel.error(data.error);
						self.testStatus('Testing failed: ' + data.error);
					} else if (data.type == 'TestResponse') {
						self.logModel.info('Testing complete.');
						self.testStatus('Testing complete.');
					} else {
						self.logModel.error('Unexpected reply: ' + JSON.stringify(data));
						self.testStatus("Unexpected: " + JSON.stringify(data));
					}
					self.doAfterTest();
				},
				failure: function(xhr, status, message) {
					console.log("test failed: ", status, message)
					self.logModel.error("HTTP request failed: " + message);
					self.testStatus('Testing error: ' + message);
					self.doAfterTest();
				}
			});
			self.activeTask(taskId);
		},
		updateTest: function(testEvent) {
			var match = ko.utils.arrayFirst(this.results(), function(item) {
				return testEvent.name === item.name;
			});
			if(!match) {
				var test = new TestResult(testEvent);
				this.results.push(test);
			} else {
				match.update(testEvent);
			}
		},
		onCompileSucceeded: function(event) {
			var self = this;
			if (self.rerunOnBuild() && !self.haveActiveTask()) {
				self.doTest(true); // true=triggeredByBuild
			}
		},
		doStop: function() {
			var self = this;
			if (self.haveActiveTask()) {
				sbt.killTask({
					taskId: self.activeTask(),
					success: function(data) {
						console.log("kill success: ", data)
					},
					failure: function(xhr, status, message) {
						console.log("kill failed: ", status, message)
						self.logModel.error("HTTP request to kill task failed: " + message)
					}
				});
			}
		},
		startButtonClicked: function(self) {
			console.log("Start was clicked");
			self.doTest(false); // false=!triggeredByBuild
		},
		restartButtonClicked: function(self) {
			console.log("Restart was clicked");
			self.doStop();
			self.restartPending(true);
		},
		stopButtonClicked: function(self) {
			console.log("Stop was clicked");
			self.restartPending(false);
			self.doStop();
		}
	});

	var testConsole = new TestDisplay();

	return api.Plugin({
		id: 'test',
		name: "Test",
		icon: 'ꙫ',
		url: "#test",
		routes: {
			'test': function() { api.setActiveWidget(testConsole); }
		},
		widgets: [testConsole]
	});
});
