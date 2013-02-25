define(['text!./browse.html', 'core/pluginapi'], function(template, api, files) {

	var ko = api.ko,
		key = api.key,
		Widget = api.Widget,
		Class = api.Class;


	var Browser = Widget({
		id: 'code-browser-view',
		template: template,
		init: function(config) {
			var self = this;
			self.directory = config.directory;
			self.pageType = ko.computed(function(o) {
				return "browser"
			});
			self.files = ko.computed(function() {
				var dir = self.directory();
				return dir.children();
			});
			self.name = ko.computed(function() {
				// TODO - Trim the name in a nicer way
				return './' + self.directory().name();
			});
			self.isEmpty = ko.computed(function() {
				return self.files().length == 0;
			});
			self.prevDirUrl = ko.computed(function() {
				var parts = self.directory().relative().split('/');
				return '#code/' + parts.slice(0, parts.length -1).join('/');
			});
		}
	});
	return Browser;

});
