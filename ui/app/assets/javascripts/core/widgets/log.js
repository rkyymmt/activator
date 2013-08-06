define(['text!./log.html', 'webjars!knockout', 'core/widget', 'core/utils', 'core/markers'], function(template, ko, Widget, utils, markers){

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


	// escapeHtml and entityMap from mustache.js MIT License
	// Copyright (c) 2010 Jan Lehnardt

	var entityMap = {
			"&": "&amp;",
			"<": "&lt;",
			">": "&gt;",
			'"': '&quot;',
			"'": '&#39;',
			"/": '&#x2F;'
	};

	function escapeHtml(string) {
		return String(string).replace(/[&<>"'\/]/g, function (s) {
			return entityMap[s];
		});
	}

	function unix(filename) {
		return filename.replace(/[\\]/g, '/');
	}

	function stripTrailing(filename) {
		if (filename.length > 0 && filename[filename.length - 1] == '/')
			return filename.substring(0, filename.length - 1);
		else
			return filename;
	}

	function startsWith(prefix, s) {
		return (prefix.length <= s.length &&
				s.substring(0, prefix.length) == prefix);
	}

	function relativizeFile(file) {
		file = unix(file);
		if ('serverAppModel' in window && 'location' in window.serverAppModel) {
			var root = stripTrailing(unix(window.serverAppModel.location));
			if (startsWith(root, file))
				return file.substring(root.length);
			else
				return file;
		} else {
			return file;
		}
	}

	// this regex is used on both the text and html-escaped log line
	var fileLineRegex = new RegExp("^(([^:]+:)?([^:]+)):([0-9]+): ");

	ko.bindingHandlers['compilerMessage'] = {
		// we only implement init, not update, because log lines are immutable anyway
		// and knockout calls update() multiple times (not smart enough to do deep
		// equality on arrays, maybe?), the multiple update() in turn can result
		// in not registering the most recent file markers, and just in inefficiency.
		init: function (element, valueAccessor, allBindingsAccessor, viewModel) {
			var o = ko.utils.unwrapObservable(valueAccessor());
			var text = ko.utils.unwrapObservable(o.message);
			var html = escapeHtml(text);
			var m = fileLineRegex.exec(text);
			var file = null;
			var line = null;
			if (m !== null) {
				file = m[1];
				line = m[4];
				// both html-escaped and second-arg-to-replace-escaped
				var relative = relativizeFile(file);
				var relativeEscaped = escapeHtml(relative).replace('$', '$$');
				// TODO include the line number in the url once code plugin can handle it
				var link = '<a href="#code'+relativeEscaped+':'+line+'">$1:$4</a>: ';
				html = html.replace(fileLineRegex, link);

				// register the error globally so editors can pick it up
				markers.registerFileMarker(ko.utils.unwrapObservable(o.markerOwner),
						relative, line, ko.utils.unwrapObservable(o.level), text);
			}
			ko.utils.setHtml(element, html);
		}
	};

	var nextMarkerOwner = 1;

	var Log = utils.Class(Widget, {
		id: 'log-widget',
		template: template,
		init: function(parameters) {
			// we keep an array of arrays because Knockout
			// needs linear time in array size to update
			// the view, so we are using lots of little
			// arrays.
			this.currentLog = ko.observableArray();
			this.logGroups = ko.observableArray([ this.currentLog ]);
			this.queue = [];
			this.boundFlush = this.flush.bind(this);
			this.node = null;
			this.markerOwner = 'log-' + nextMarkerOwner;
			nextMarkerOwner += 1;
		},
		onRender: function(childNodes) {
			this.node = $(childNodes).parent();

			// force scroll to bottom to start, in case
			// when we render we already have a page full
			// of lines.
			var state = this.findScrollState();
			state.wasAtBottom = true;
			this.applyScrollState(state);
		},
		findScrollElement: function() {
			var element = null;
			if (this.node !== null) {
				// Look for the node that we intend to have the scrollbar.
				// If no 'auto' node found, just use our own node
				// (which is probably wrong).
				var logsListNode = $(this.node).children('ul.logsList')[0];
				var parents = [ logsListNode ].concat($(logsListNode).parents().get());
				element = parents[0];
				var i = 0;
				for (; i < parents.length; ++i) {
					var scrollMode = $(parents[i]).css('overflowY');
					if (scrollMode == 'auto' || scrollMode == 'scroll') {
						element = parents[i];
						break;
					}
				}
			}
			return element;
		},
		findScrollState: function() {
			var element = this.findScrollElement();
			var state = { wasAtBottom: true };
			if (element !== null) {
				// if we're within twenty pixels of the bottom, stick to the bottom;
				// if we require being *exactly* at the bottom it can feel like it's
				// too hard to get there.
				state.wasAtBottom = (element.scrollHeight - element.clientHeight - element.scrollTop) < 20;
				state.scrollTop = element.scrollTop;
			}
			return state;
		},
		applyScrollState: function(state) {
			var element = this.findScrollElement();
			if (element !== null) {
				if (state.wasAtBottom) {
					// stay on the bottom if we were on the bottom
					element.scrollTop = (element.scrollHeight - element.clientHeight);
				} else {
					element.scrollTop = state.scrollTop;
				}
			}
		},
		flush: function() {
			if (this.queue.length > 0) {
				var state = this.findScrollState();

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

				this.applyScrollState(state);
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
			this.queue.push({ level: level, message: message, markerOwner: this.markerOwner });

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
			markers.clearFileMarkers(this.markerOwner);
			this.logGroups.push(this.currentLog);
		},
		moveFrom: function(other) {
			// "other" is another logs widget
			other.flush();
			this.flush();
			var removed = other.logGroups.removeAll();
			markers.clearFileMarkers(other.markerOwner);
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
