define(['text!./test.html', 'css!./test.css', 'core/pluginapi'], function(template, css, api) {
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

	// TODO - Copy-pasted from run.  We should move this to a util library *OR* just
	// not do it at all.
	// if we wanted to be cute we'd convert these to HTML tags perhaps
	var ansiCodeRegex = new RegExp("\\033\\[[0-9;]+m", "g");
	var stripAnsiCodes = function(s) {
		return s.replace(ansiCodeRegex, "");
	}


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
			self.logs = ko.observableArray();
			self.waiting = ko.observable(false);
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
		},
		filterTests: function() {
			// TODO - More states.
			if(this.testFilter() == 'all') {
				this.testFilter('failures')
			} else {
				this.testFilter('all')
			}
		},
		runTests: function() {
			// TODO - Make sbt call here.
			var self = this;
			console.log('Running tests...')
			self.testStatus('Running tests...')
			self.waiting(true);
			// TODO - Do we want to clear the test data we had previously
			// or append?  Tests may disappear and we'd never know...
			sbt.runTask({
				task: 'TestRequest',
				onmessage: function(event) {
					if('type' in event && event.type == 'TestEvent') {
						self.updateTest(event);
					} else if ('type' in event && event.type == 'LogEvent') {
						var message = event.entry.message;
						var logType = event.entry.type;
						if (logType == 'stdout' || logType == 'stderr') {
							self.logs.push(stripAnsiCodes(message))
						} else if (logType == 'message') {
							self.logs.push(event.entry.level + ": " + stripAnsiCodes(message));
						} else {
							self.logs.push(logType + ": " + stripAnsiCodes(message));
						}
					}
					// TODO - Should we show logs?
					// TODO - Should we be able to query for test console output?
				},
				success: function(data) {
					self.testStatus('Testing complete');
					self.waiting(false);
				},
				failure: function(err) {
					console.log("test failed: ", err)
					self.testStatus('Testing failed: ' + err.responseText);
					self.waiting(false);
				}
			});
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
