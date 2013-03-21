define(['text!./fileselection.html', 'vendors/knockout-2.2.1.debug', 'core/widget'], function(template, ko, Widget) {

	function browse(location) {
		return $.ajax({
			url: '/api/local/browse',
			type: 'GET',
			dataType: 'json',
			data: {
				location: location
			}
		});
	};

	// File model...
	function File(config) {
		var self = this;
		self.name = config.name;
		self.location = config.location;
		self.isDirectory = config.isDirectory;
		self.isFile = !config.isDirectory;
		self.highlighted = ko.observable(false);
	};
	// Function for filtering...
	function fileIsHighlighted(file) {
		return file.highlighted();
	};
	function noop() {}

	var FileSelection = Widget({
		id: 'file-selection-widget',
		template: template,
		init: function(config) {
			var cfg = config || {};
			var self = this;
			// TODO - Allow context...
			self.onSelect = config.onSelect || noop;
			self.onCancel = config.onCancel || noop;
			self.showFiles = ko.observable(cfg.showFiles || false);
			self.currentFiles = ko.observableArray([]);
			self.currentHighlight = ko.computed(function() {
				return $.grep(self.currentFiles(), fileIsHighlighted)[0];
			});
			self.selectedFile = ko.observable();
			self.currentViewFiles = ko.computed(function() {
				var showFiles = self.showFiles();
				return $.grep(self.currentFiles(), function(file){
					if(file.isDirectory || showFiles) {
						return true;
					}
					return false;
				});
			});
			if(cfg.initialDir) {
				this.load(cfg.initialDir);
			}
		},
		highlight: function(file) {
			$.each($.grep(this.currentFiles(), fileIsHighlighted), function(idx, item) {
				item.highlighted(false);
			});
			file.highlighted(true);
		},
		load: function(dir) {
			var self = this;
			browse(dir).done(function(values) {
				self.currentFiles($.map(values.children || [], function(config) {
					return new File(config);
				}));
			}).error(function() {
				alert('Failed to load directory listing for: ' + dir);
			});
		},
		select: function() {
			var currentFile = this.currentHighlight();
			// TODO - Issue error if nothing selected.
			this.onSelect(currentFile);
		},
		cancel: function() {
			this.onCancel();
		}
	});
	return FileSelection;
});
