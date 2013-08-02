define(['core/pluginapi'], function(api) {

	var ko = api.ko;

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

	function rename(location, newName) {
		return $.ajax({
			url: '/api/local/rename',
			type: 'PUT',
			dataType: 'text',
			data: {
				location: location,
				newName: newName
			}
		});
	}

	// A model for files that works directly off a location, and
	// nothing else.
	var FileModel = api.Class({
		init: function(config) {
			var self = this;
			// TODO - Split this into relative + absolute/canonical locations...
			self.location = config.location;
			self.name = ko.observable(config.name || '');
			self.editingText = ko.observable(self.name()); // NOT a computed
			self.editing = ko.observable(false); // in edit mode?
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
			self.singleClickPending = false;
			self.editing.subscribe(self.onEditingChanged.bind(self));
			self.reloadSelf = self.loadInfo.bind(self);
			if (typeof(config.reloadParent) == 'function') {
				self.reloadParent = config.reloadParent;
			}
		},
		reloadParent: function() {
			// no-op default, usually overridden except for root dir
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
			var newLookup = {};
			$.each(config.children || [], function(idx, childConfig) {
				if (children[childConfig.name]) {
					children[childConfig.name].updateWith(childConfig);
					newLookup[childConfig.name] = children[childConfig.name];
				} else {
					childConfig.reloadParent = self.reloadSelf;
					var f = new FileModel(childConfig);
					newChildren.push(f);
					newLookup[childConfig.name] = f;
				}
			});

			var oldChildren = self.children();
			var withoutRemovedChildren = $.grep(oldChildren, function(child, idx) {
				return (child.name() in newLookup);
			});

			if (newChildren.length > 0 || (withoutRemovedChildren.length < oldChildren.length)) {
				var allChildren = withoutRemovedChildren.concat(newChildren);
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
		},
		afterSingleClickTimeout: function() {
			if (this.singleClickPending) {
				this.singleClickPending = false;
				// we didn't double click, so select the item
				this.select();
			}
		},
		onSingleClick: function() {
			this.singleClickPending = true;
			setTimeout(this.afterSingleClickTimeout.bind(this), 400);
		},
		onDoubleClick: function() {
			this.singleClickPending = false;
			this.startEditing();
		},
		onKeyUp: function(data, event) {
			// on Escape, undo edits and leave edit mode
			if (event.keyCode == 27) {
				this.editingText(this.name());
				this.editing(false);
			// on Enter, commit without resetting
			} else if (event.keyCode == 13) {
				this.editing(false);
			}
			return false;
		},
		onBlur: function(data, event) {
			this.editing(false);
		},
		startEditing: function() {
			this.editingText(this.name());
			this.editing(true);
		},
		onEditingChanged: function(newValue) {
			var self = this;
			if (!newValue) {
				var newName = this.editingText();
				if (newName.length > 0 && newName != this.name()) {
					rename(this.location, newName).done(function() {
						console.log("Rename success");
						self.reloadParent();
					}).fail(function(err) {
						console.log("Failed to rename: ", err);
						alert(err.responseText);
					});
				}
			}
		}
	});

	return {
		FileModel: FileModel
	};
});
