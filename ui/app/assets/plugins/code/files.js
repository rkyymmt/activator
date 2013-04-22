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
			self.isDirectory = ko.observable(config.isDirectory || false);
			self.mimeType = ko.observable(config.mimeType);
			self.type = ko.observable(config.type);
			self.size = ko.observable(config.size);
			self.children = ko.observableArray([]);
			self.childLookup = ko.computed(function() {
				var lookup = {};
				$.each(self.children(), function(idx, child) {
					lookup[child.name()] = child;
				});
				return lookup;
			});
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
			// TODO - Rate limit this...
			var self = this;
			browse(this.location).done(function(values) {
				self.updateWith(values);
			}).error(function() {
				alert('Failed to load information about file: ' + self.location)
			});
			return self;
		},
		updateWith: function(config) {
			var self = this;
			if(config.name) self.name(config.name);
			if(config.mimeType) self.mimeType(config.mimeType);
			if(config.name) self.name(config.name);
			if(config.isDirectory) self.isDirectory(config.isDirectory);
			if(config.type) self.type(config.type);

			// we build up the new child list in a temp array
			// so we aren't constantly updating the UI as we
			// fill in the list.
			var newChildren = [];
			var children = self.childLookup();
			$.each(config.children || [], function(idx, config) {
				if(children[config.name]) {
					children[config.name].updateWith(config);
				} else {
					newChildren.push(new FileModel(config));
				}
			});
			if (newChildren.length > 0) {
				var oldChildren = self.children();
				var allChildren = oldChildren.concat(newChildren);
				allChildren.sort(function(a, b) {
					if (a.isDirectory() && !b.isDirectory())
						return -1;
					else if (!a.isDirectory() && b.isDirectory())
						return 1;
					else
						return a.name().localeCompare(b.name());
				});
				// load all children to the UI at once.
				self.children(allChildren);
			}
		},
		loadContents: function() {
			var self = this;
			show(self.location).done(function(contents) {
				self.contents(contents);
				self.isContentsDirty(false);
			}).error(function() {
				// TODO - Figure out alerting!
				alert("Failed to load file: " + self.location)
			});
			return self;
		},
		saveContents: function() {
			var self = this;
			save(self.location, self.contents()).done(function(){
				self.isContentsDirty(false);
			}).error(function(){
				//TODO - figure out alerting!
				alert('Failed to save file: '+ self.location)
			});
		},
		toString: function() {
			return 'File['+this.location+']';
		}
	});


	return {
		FileModel: FileModel
	};
});
