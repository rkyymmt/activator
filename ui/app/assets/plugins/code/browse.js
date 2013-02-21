define(['text!./browse.html', 'core/pluginapi'], function(template, api, files) {

	var ko = api.ko,
		key = api.key,
		Widget = api.Widget,
		Class = api.Class;


	var Browser = Widget({
		id: 'code-browser-view',
		template: template,
		init: function(config) {
			this.directory = config.directory;
			this.pageType = ko.computed(function(o) {
				return "browser"
			}, this);
		}
	});
	return Browser;

});
