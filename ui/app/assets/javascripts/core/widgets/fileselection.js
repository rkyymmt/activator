define(['css!./fileselection.css', 'text!./fileselection.html', 'vendors/knockout-2.2.1.debug', 'core/widget'], function(css, template, ko, Widget) {

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

	function browseRoots() {
		return $.ajax({
				url: '/api/local/browseRoots',
				type: 'GET',
				dataType: 'json'
		});
	};

	// File model...
	function File(config) {
		var self = this;
		self.name = config.name || config.humanLocation;
		self.location = config.location;
		self.humanLocation = config.humanLocation;
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
			self.shownDirectory = ko.observable(cfg.initialDir || '');
			self.currentFiles = ko.observableArray([]);
			self.currentHighlight = ko.computed(function() {
				return $.grep(self.currentFiles(), fileIsHighlighted)[0];
			});
			self.hasCurrentHighlight = ko.computed(function() {
				if(self.currentHighlight()) {
					return true;
				}
				return false;
			});
			self.currentViewFiles = ko.computed(function() {
				var showFiles = self.showFiles();
				return $.grep(self.currentFiles(), function(file){
					if(file.isDirectory || showFiles) {
						return true;
					}
					return false;
				});
			});
			self.title = ko.observable(cfg.title || "Select a file")
			if(cfg.initialDir) {
				this.load(cfg.initialDir);
			} else {
				this.loadRoots();
			}
		},
		click: function(file) {
			if(file == this.currentHighlight()) {
				if(file.location) {
					this.load(file.location);
				} else {
					// TODO - Only on windows.
					this.loadRoots();
				}
			} else {
				this.highlight(file);
			}
		},
		highlight: function(file) {
			$.each($.grep(this.currentFiles(), fileIsHighlighted), function(idx, item) {
				item.highlighted(false);
			});
			file.highlighted(true);
		},
		loadRoots: function(dir) {
			var self = this;
			browseRoots().done(function(values) {
					self.currentFiles($.map(values, function(config) {
							return new File(config);
					}));
			}).error(function() {
				alert('Failed to load file system roots.');
			});
		},
		load: function(dir) {
			var self = this;
			browse(dir).done(function(values) {
				self.shownDirectory(dir);
				var fileConfigs = [{
						name: '.',
						isDirectory: values.isDirectory,
						location: values.location,
						humanLocation: values.humanLocation
				}];
				// TODO - see if we need to add "back" directory.
				if(values.parent && values.parent.isDirectory) {
					fileConfigs.push({
						name: '..',
						isDirectory: true,
						location: values.parent.location,
						humanLocation: values.parent.humanLocation
					})
				} else if(values.isRoot) {
					// TODO - Only on windows
					// Add root marker...
					fileConfigs.push({
							name: '..',
							isDirectory: true,
							humanLocation: 'Show All Drives'
					});
				}
				fileConfigs.push.apply(fileConfigs, values.children || []);
				self.currentFiles($.map(fileConfigs, function(config) {
					return new File(config);
				}));
			}).error(function() {
				alert('Failed to load directory listing for: ' + dir);
			});
		},
		select: function() {
			var currentFile = this.currentHighlight();
			if(currentFile) {
				this.onSelect(currentFile.location);
			} else {
				this.onSelect(this.shownDirectory());
			}
		},
		cancel: function() {
			this.onCancel();
		}
	});
	return FileSelection;
});
