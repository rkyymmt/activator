define(['text!./test.html', 'css!./test.css', 'core/pluginapi'], function(template, css, api) {
	var ko = api.ko;
	var sbt = api.sbt;

	// ---- EVERYTHING HERE AND BELOW IS A TESTING HACK ----
	var testingResults = [{
		testName: 'test.Test',
		result: 'pass',
		description: 'With flying colors'
	},{
		testName: 'test.Test2',
		result: 'fail',
		description: 'It was ugly.'
	},{
		testName: 'generic.test.suite.Stuff',
		result: 'pass',
		description: 'ZOMG!!!! AMAZING.'
	},{
		testName: 'test.Test3',
		result: 'error',
		description: 'It was a big explosion.'
	},{
		testName: 'test.Test3',
		result: 'pending',
		description: 'Still running....'
	}];

	// Fakes the streaming API for us if SBT fails.
	var LazyCheatStreamStuff = function(displayWidget, events) {
		var timeouts = $.map(events, function(item, idx)  {
			return window.setTimeout(function() {
				displayWidget.updateTest(item);
				window.clearTimeout(timeouts[idx]);
			}, 2000 * idx);
		});
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
			// TODO - don't stub, actually run tests.
			self.results = ko.observableArray();
			self.testStatus = ko.observable('Waiting to test');
			// TODO - Add a boolean so we can disable
			// the run button if we're currently running tests.
			// TODO - Store state beyond the scope of this widget!
			// We should probably be listening to tests *always*
			// and displaying latest status *always*.
			self.hasResults = ko.computed(function() {
				return self.results().length > 0;
			});
		},
		runTests: function() {
			// TODO - Make sbt call here.
			var self = this;
			console.log('Running tests...')
			self.testStatus('Running tests...')
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
				},
				failure: function(err) {
					console.log("test failed: ", err)
					self.testStatus('Testing failed: ' + err.responseText);
					// TODO - Stop stubbing data when Havoc's part is complete.
					LazyCheatStreamStuff(self, testingResults);
				}
			});
		},
		updateTest: function(testEvent) {
			// TODO - Find if we have the test already
			var test = new TestResult(testEvent);
			this.results.push(test);
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
