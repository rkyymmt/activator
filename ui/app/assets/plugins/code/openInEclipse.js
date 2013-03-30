define(['text!./openInEclipse.html',  'text!./openInEclipseContent.html', 'core/pluginapi', 'core/widgets/overlay', 'core/log', 'core/templates'],
function(template, contentTemplate, api, Overlay, log, templates){

	var ko = api.ko;
	var sbt = api.sbt;

	var contentView = templates.registerTemplate('open-in-eclipse-content', contentTemplate);

	function browse(location) {
		return $.ajax({
			url: '/api/local/browse',
			type: 'GET',
			dataType: 'json',
			data: {
				location: location
		}
		});
	}

	var OpenInEclipse = api.Widget({
		id: 'open-in-eclipse-widget',
		template: template,
		init: function(parameters) {
			var self = this;
			self.overlay = new Overlay({ contentView: contentView, contentModel: self });
			self.log = new log.Log();
			self.haveProjectFiles = ko.observable(false);
			self.workingStatus = ko.observable("");
			self.projectDirectory = ko.observable(serverAppModel.location);
			self.node = null;
			self.activeTask = ko.observable(""); // empty string or taskId
		},
		onRender: function(childElements) {
			var self = this;

			if (self.id != 'open-in-eclipse-widget')
				throw new Error("wrong this in onRender " + self);
			if (self.node !== null)
				throw new Error("rendering OpenInEclipse twice");

			self.node = $(childElements[0]).parent();
			if (!self.node)
				throw new Error("didn't get the eclipse node");

			self.startNode = self.node.find('.start');
			self.generateNode = self.node.find('.generate');
			self.instructionsNode = self.node.find('.instructions');
		},
		open: function() {
			if (this.node === null)
				throw new Error("haven't been rendered yet");
			this._switchTo(null); // show nothing until start() does
			this.node.show();
			this.overlay.open();
			this.start();
		},
		close: function() {
			this.overlay.close();
		},
		_updateHaveProjectFiles: function() {
			var self = this;
			browse(serverAppModel.location + "/.project").done(function(data) {
				self.haveProjectFiles(true);
			}).error(function() {
				self.haveProjectFiles(false);
			});
		},
		// node may be null to hide all pages
		_switchTo: function(node) {
			var self = this;
			var nodes = [self.startNode, self.generateNode, self.instructionsNode];
			$.each(nodes, function(i, value) {
				if (value === node) {
					value.fadeIn();
				} else {
					value.hide();
				}
			});
		},
		start: function() {
			this._updateHaveProjectFiles();
			this._switchTo(this.startNode);
		},
		generate: function() {
			var self = this;
			this._switchTo(this.generateNode);
			if (self.activeTask() == "") {
				self.workingStatus("Generating Eclipse project files...");
				self.log.clear();
				var taskId = sbt.runTask({
					task: 'eclipse',
					onmessage: function(event) {
						console.log("event while generating eclipse ", event);
						self.log.event(event);
					},
					success: function(data) {
						console.log("eclipse result", data);
						self.workingStatus("Successfully created Eclipse project files.");
						self._updateHaveProjectFiles();
						self.activeTask("");
					},
					failure: function(status, message) {
						console.log("eclipse fail", message);
						self.workingStatus("Failed to generate Eclipse project files.");
						self._updateHaveProjectFiles();
						self.activeTask("");
					}
				});
				self.activeTask(taskId);
			}
		},
		instructions: function() {
			this._switchTo(this.instructionsNode);
		}
	});

	return OpenInEclipse;
});
