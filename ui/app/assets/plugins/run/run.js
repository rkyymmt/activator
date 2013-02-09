define(['text!./run.html', 'core/streams'], function(template, Streams){

	var ko = req('vendors/knockout-2.2.1.debug');

	var randomShort = function() {
		return Math.floor(Math.random() * 65536)
	}

	var genTaskId = function(prefix) {
		return prefix + "-" + (new Date().getTime()) + "-" + randomShort() + "-" + randomShort() + "-" + randomShort()
	}

	// if we wanted to be cute we'd convert these to HTML tags perhaps
	var ansiCodeRegex = new RegExp("\\033\\[[0-9;]+m", "g");
	var stripAnsiCodes = function(s) {
		return s.replace(ansiCodeRegex, "");
	}

	var Run = Widget({
		id: 'play-run-widget',
		template: template,
		init: function(parameters){
			this.title = ko.observable("Run");
			this.logs = ko.observableArray();
		},
		update: function(parameters){
		},
		runButtonClicked: function(self) {
			console.log("Run was clicked");
			// TODO factor all this out into some kind of SBT API
			var runRequest = {
				appId: serverAppModel.id,
				taskId: genTaskId(serverAppModel.id),
				description: "Run " + serverAppModel.name,
				task: {
					type: "RunRequest"
				}
			};
			Streams.subscribeTask(runRequest.taskId, function(event) {
				if ('type' in event && event.type == 'LogEvent') {
					var message = event.entry.message;
					var logType = event.entry.type;
					self.logs.push(logType + ": " + stripAnsiCodes(message));
				} else {
					self.logs.push("unknown event: " + JSON.stringify(event))
				}
			})
			self.logs.push("Running...\n");
			$.ajax({
				url: '/api/sbt/task',
				type: 'POST',
				dataType: 'json', // return type
				contentType: 'application/json; charset=utf-8',
				data: JSON.stringify(runRequest),
				success: function(data) {
					console.log("run result: ", data);
					if (data.type == 'ErrorResponse') {
						self.logs.push(data.error);
					} else if (data.type == 'RunResponse') {
						self.logs.push('Run complete.');
					} else {
						self.logs.push('Unexpected reply: ' + JSON.stringify(data));
					}
				},
				failure: function(message) {
					console.log("run failed: ", message)
					self.logs.push("HTTP request failed: " + message);
				}
			});
		}
	});

	return {
		id: 'run',
		name: "Run",
		icon: "▶",
		url: "#run",
		routes: {
			'run': function(bcs) {
				return $.map(bcs, function(crumb) {
					return {
						widget: Run
					};
				});
			}
		}
	};
});
