define(['css!./code.css', 'text!./browse.html'], function(css, template) {

	var ko = req('vendors/knockout-2.2.1.debug'),
		key = req('vendors/keymage.min');

	var FileModel = Class({
		init: function(config) {
			this.name = config.name;
			this.location = config.location;
			this.isDirectory = config.isDirectory;
			this.mimeType = config.mimeType;
			var path = ['code'];
			var relative = config.location.replace(serverAppModel.location, "");
			if(relative[0] == '/') {
				relative = relative.slice(1);
			}
			// TODO - Is it ok to drop browse history when viewing a file?
			this.url = 'code/' + relative;
		},
		select: function() {
			window.location.hash = this.url;
		}
	});

	var Browser = Widget({
		id: 'code-browser-view',
		template: template,
		dataIndex: 1,
		init: function(parameters, datas) {
			this.url = parameters.fileLoc;
			var children = datas.children || [];
			this.tree = ko.observableArray($.map(children, function(config) {
				return new FileModel(config);
			}));
			this.pageType = ko.computed(function(o) {
				return "browser"
			}, this);
		},
		onRender: function(views) {
			var ul = $(views[2])
			if(!ul.find(".active").length) {
				ul.find("li,dd").first().addClass("active")
			}
		}
	});
	return Browser;

})