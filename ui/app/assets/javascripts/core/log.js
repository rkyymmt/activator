define(['text!./log.html', 'core/pluginapi'], function(template, api){

	var ko = api.ko;

	// if we wanted to be cute we'd convert these to HTML tags perhaps
	var ansiCodeRegex = new RegExp("\\033\\[[0-9;]+m", "g");
	var stripAnsiCodes = function(s) {
		return s.replace(ansiCodeRegex, "");
	}

	var Log = api.Widget({
		id: 'log-widget',
		template: template,
		init: function(parameters) {
			this.logs = ko.observableArray();
		},
		log: function(level, message) {
			this.logs.push({ level: level, message: message });
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
			return this.logs.removeAll();
		},
		moveFrom: function(other) {
			// "other" is another logs widget
			var removed = other.logs.removeAll();
			ko.utils.arrayPushAll(this.logs, removed);
			this.logs.valueHasMutated();
		},
		// returns true if it was a log event
		event: function(event) {
			if ('type' in event && event.type == 'LogEvent') {
				var message = event.entry.message;
				var logType = event.entry.type;
				if (logType == 'stdout') {
					this.stdout(stripAnsiCodes(message));
				} else if (logType == 'stderr') {
					this.stderr(stripAnsiCodes(message));
				} else if (logType == 'message') {
					this.log(event.entry.level, stripAnsiCodes(message));
				} else {
					// this handles "success"
					this.log(logType, stripAnsiCodes(message));
				}
				return true;
			} else {
				return false;
			}
		}
	});

	return { Log: Log };
});
