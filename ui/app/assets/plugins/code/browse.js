define(['text!./browse.html', 'core/pluginapi'], function(template, api) {

	var ko = api.ko,
		key = api.key,
		Widget = api.Widget,
		Class = api.Class;

	// TODO - Don't duplicate this in both view.js + browse.js...
	function open(location) {
		return $.ajax({
			url: '/api/local/open',
			type: 'GET',
			data: {
				location: location
			}
		});
	}

	var Browser = Widget({
		id: 'code-browser-view',
		template: template,
		init: function(config) {
			var self = this;
			self.openInEclipse = config.openInEclipse;
			self.openInIdea = config.openInIdea;
			self.directory = config.directory;
			self.pageType = ko.computed(function(o) {
				return "browser"
			});
			self.files = ko.computed(function() {
				var dir = self.directory();
				return dir.children();
			});
			self.rootAppPath = config.rootAppPath || config.directory().location;
			self.name = ko.computed(function() {
				// TODO - Trim the name in a nicer way
				return './' + self.directory().name();
			});
			self.isEmpty = ko.computed(function() {
				return self.files().length == 0;
			});
			self.parts = ko.computed(function() {
				var parts = self.directory().relative().split('/');
				return $.map(parts, function(name, idx) {
					return {
						name: name,
						url: '#code/' + parts.slice(0, idx+1).join('/')
					};
				});
			});
			self.prevDirUrl = ko.computed(function() {
				var parts = self.directory().relative().split('/');
				return '#code/' + parts.slice(0, parts.length -1).join('/');
			});
		},
		openInFileBrowser: function() {
			var self = this;
			var loc = self.directory().location;
			open(loc).success(function() {}).error(function(err) {
				console.log('Failed to open directory in browser: ', err)
				alert('Failed to open directory.  This may be unsupported by your system.');
			});
		},
		openProjectInFileBrowser: function() {
			var self = this;
			var loc = self.rootAppPath;
			open(loc).success(function() {}).error(function(err) {
				console.log('Failed to open directory in browser: ', err)
				alert('Failed to open directory.  This may be unsupported by your system.');
			});
		}
	});
	return Browser;

});
