define(['text!./test.html', 'css!./test.css', 'core/pluginapi'], function(template, css, api) {
	var ko = api.ko;
	var sbt = api.sbt;

	// ---- EVERYTHING HERE AND BELOW IS A TESTING HACK ----
	function randomStatus() {
		var result = Math.random();
		if(result > 0.4) {
			return 'pass';
		}
		if(result > 0.2) {
			return 'fail';
		}
		if(result > 0.1) {
			return 'error';
		}
		return 'pending';
	}
	function makeTestStatus(name) {
		return {
			testName: name,
			result: randomStatus(),
			description: 'PUT STUFF HERE'
		};
	}
	function makeTestingResults() {
		return $.map(['test.Works', 'test.Werks', 'test.Stuff', 'AbstractProxyBeanFactoryTest'], function(name) {
			return makeTestStatus(name);
		});
	}

	// Fakes the streaming API for us if SBT fails.
	var LazyCheatStreamStuff = function(displayWidget, events) {
		displayWidget.waiting(true);
		var timeouts = $.map(events, function(item, idx)  {
			return window.setTimeout(function() {
				displayWidget.updateTest(item);
				window.clearTimeout(timeouts[idx]);
			}, 2000 * idx);
		});

		var finalTo = window.setTimeout(function() {
			displayWidget.waiting(false);
			displayWidget.testStatus('Completed');
			window.clearTimeout(finalTo);
		}, 2000 * events.length + 2000)
	}
	// ---- EVERYTHING HERE AND ABOVE IS A TESTING HACK ----

	// TODO - Other widgety things here.
	var TestResult = api.Class({
		init: function(config) {
			var self = this;
			self.testName = config.testName;
			self.result = ko.observable(config.result);
			self.description = ko.observable(config.description);
			self.resultClass = ko.computed(function() {
				// TODO - handle real strings...
				if(self.result() == 'pass') {
					return 'pass';
				}
				if(self.result() == 'pending') {
					return 'pending';
				}
				return 'fail';
			});
		},
		// Update our state from an event.
		update: function(event) {
			this.description(event.description);
			this.result(event.result);
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
						return item.result() != 'pass';
					});
				}
				return self.results();
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
						self.udpateTest(event);
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
					// TODO - Stop stubbing data when Havoc's part is complete.
					LazyCheatStreamStuff(self, makeTestingResults());
				}
			});
		},
		updateTest: function(testEvent) {
			var match = ko.utils.arrayFirst(this.results(), function(item) {
				return testEvent.testName === item.testName;
			});
			if(!match) {
				var test = new TestResult(testEvent);
				this.results.push(test);
			} else {
				match.update(testEvent);
			}
		}
	});
	return {
		id: 'test',
		name: "Test",
		icon: '𐑏',
		url: "#test",
		routes: {
			'test': [TestDisplay]
		}
	};
});
