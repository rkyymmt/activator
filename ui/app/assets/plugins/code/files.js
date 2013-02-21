define(['core/pluginapi'], function(api) {

	var ko = api.ko,
		key = api.key,
		Widget = api.Widget,
		Class = api.Class;

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
	// Fetch utility
	function show(location){
		return $.ajax({
			url: '/api/local/show',
			type: 'GET',
			dataType: 'text',
			data: { location: location }
		});
	}
	function save(location, code) {
		return $.ajax({
			url: '/api/local/save',
			type: 'PUT',
			dataType: 'text',
			data: {
				location: location,
				content: code
			}
		});
	}

	// A model for files that works directly off a location, and
	// nothing else.
	var FileModel = Class({
		init: function(config) {
			var self = this;
			// TODO - Split this into relative + absolute/canonical locations...
			self.location = config.location;
			self.name = ko.observable(config.name || '');
			self.isDirectory = ko.observable(false);
			self.mimeType = ko.observable();
			self.children = ko.observableArray([]);
			self.relative = ko.computed(function() {
				// TODO - If we have a symlink, we're f'd here if it's resolved to real location.
				// in the future we probably pass full symlink path separately.
				var relative = config.location.replace(serverAppModel.location, "");
				if(relative[0] == '/') {
					relative = relative.slice(1);
				}
				return relative;
			});
			// TODO - Is it ok to drop browse history when viewing a file?
			self.url = ko.computed(function() { return 'code/' + self.relative(); });
			self.contents = ko.observable();
			self.isContentsDirty = ko.observable(false);
			if(config.autoLoad) {
				self.loadInfo();
			}
		},
		select: function() {
			window.location.hash = this.url();
		},
		loadInfo: function() {
			var self = this;
			browse(this.location).done(function(values) {
				self.name(values.name);
				self.mimeType(values.mimeType);
				self.name(values.name);
				self.isDirectory(values.isDirectory);
				self.children($.map(values.children || [], function(config) {
					return new FileModel(config);
				}));
			}).error(function() {
				alert('Failed to load information about file: ' + self.location)
			});
		},
		loadContents: function() {
			var self = this;
			show(self.location).done(function(contents) {
				self.contents(contents);
				self.isContentsDirty(false);
			}).error(function() {
				// TODO - Figure out alerting!
				alert("Failed to load file: " + self.location)
			})
		},
		saveContents: function() {
			var self = this;
			save(self.location, self.contents()).done(function(){
				self.isContentsDirty(false);
			}).error(function(){
				//TODO - figure out alerting!
				alert('Failed to save file: '+ self.location)
			});
		}
	});


	return {
		FileModel: FileModel
	};
});
