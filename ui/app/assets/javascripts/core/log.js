define(['text!./log.html', 'core/pluginapi'], function(template, api){

	var ko = api.ko;

	// TODO we should move both the ANSI stripping and the heuristic
	// parseLogLevel to the server side. We could also use
	// Djline.terminal=jline.UnsupportedTerminal when we launch
	// sbt on the server to avoid stripping ansi codes.

	var ansiCodeString = "\\033\\[[0-9;]+m";
	// if we wanted to be cute we'd convert these to HTML tags perhaps
	var ansiCodeRegex = new RegExp(ansiCodeString, "g");
	function stripAnsiCodes(s) {
		return s.replace(ansiCodeRegex, "");
	}

	var logLevelWithCodesRegex = new RegExp("^" + ansiCodeString + "\[" +
			ansiCodeString + "(debug|info|warn|error|success)" +
			ansiCodeString + "\] (.*)");
	var logLevelRegex = new RegExp("^\[(debug|info|warn|error|success)\] (.*)");
	function parseLogLevel(level, message) {
		if (level == 'stdout' || level == 'stderr') {
			var m = logLevelWithCodesRegex.exec(message);
			if (m !== null) {
				return { level: m[1], message: m[2] };
			}
			m = logLevelRegex.exec(message);
			if (m !== null) {
				return { level: m[1], message: m[2] };
			}
		}
		return { level: level, message: message };
	};

	var Log = api.Widget({
		id: 'log-widget',
		template: template,
		init: function(parameters) {
			// we keep an array of arrays because Knockout
			// needs linear time in array size to update
			// the view, so we are using lots of little
			// arrays.
			this.currentLog = ko.observableArray();
			this.logGroups = ko.observableArray([ this.currentLog ]);
			this.tail = ko.observable(true);
			this.queue = [];
			this.boundFlush = this.flush.bind(this);
			this.node = null;
		},
		onRender: function(childNodes) {
			this.node = $(childNodes).parent();
		},
		flush: function() {
			if (this.queue.length > 0) {
				var toPush = this.queue;
				this.queue = [];
				ko.utils.arrayPushAll(this.currentLog(), toPush);
				this.currentLog.valueHasMutated();

				// 100 could probably be higher, but already lets
				// us scale the logs up by probably 100x what they
				// could be otherwise by keeping observable arrays
				// small enough.
				if (this.currentLog().length > 100) {
					this.currentLog = ko.observableArray();
					this.logGroups.push(this.currentLog);
				}

				if (this.tail()) {
					// autoScroll doesn't work for adding to the child
					// log instead of to logGroups, so do it manually
					// here. we can't use autoScroll for containerless
					// knockout bindings.
					var end = $(this.node).last()[0];
					if ('scrollIntoView' in end) {
						end.scrollIntoView(false); // true=alignWithTop
					}
				}
			}
		},
		log: function(level, message) {
			// because knockout array modifications are linear
			// time and space in array size (it computes a diff
			// every time), we try to batch them up and minimize
			// the problem. Unfortunately the diff can still end
			// up taking a long time but batching makes it an
			// annoying rather than disastrous issue for users.
			// The main mitigation for the problem is our nested array
			// (logGroups) but this helps a bit too perhaps.
			this.queue.push({ level: level, message: message });
			if (this.queue.length == 1) {
				// 100ms = threshold for user-perceptible slowness
				// in general but nobody has much expectation for
				// the exact moment a log message appears.
				setTimeout(this.boundFlush, 150);
			}
		},
		debug: function(message) {
			this.log("debug", message);
		},
		info: function(message) {
			this.log("info", message);
		},
		warn: function(message) {
			this.log("warn", message);
		},
		error: function(message) {
			this.log("error", message);
		},
		stderr: function(message) {
			this.log("stderr", message);
		},
		stdout: function(message) {
			this.log("stdout", message);
		},
		clear: function() {
			this.flush(); // be sure we collect the queue
			this.currentLog = ko.observableArray();
			this.logGroups.removeAll();
			this.logGroups.push(this.currentLog);
		},
		moveFrom: function(other) {
			// "other" is another logs widget
			other.flush();
			this.flush();
			var removed = other.logGroups.removeAll();
			ko.utils.arrayPushAll(this.logGroups(), removed);
			this.logGroups.valueHasMutated();
		},
		// returns true if it was a log event
		event: function(event) {
			if (event.type == 'LogEvent') {
				var message = event.entry.message;
				var logType = event.entry.type;
				if (logType == 'message') {
					this.log(event.entry.level, stripAnsiCodes(message));
				} else {
					if (logType == 'success') {
						this.log(logType, stripAnsiCodes(message));
					} else {
						// sometimes we get stuff on stdout/stderr before
						// we've intercepted sbt's logger, so try to parse
						// the log level out of the [info] that sbt prepends.
						var m = parseLogLevel(logType, message);
						this.log(m.level, stripAnsiCodes(m.message));
					}
				}
				return true;
			} else {
				return false;
			}
		},
		leftoverEvent: function(event) {
			if (event.type == 'RequestReceivedEvent' || event.type == 'Started') {
				// not interesting
			} else {
				this.warn("ignored event: " + JSON.stringify(event));
			}
		}
	});

	return { Log: Log };
});
