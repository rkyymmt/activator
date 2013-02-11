define(['css!./code.css', 'text!./browse.html', 'core/pluginapi'], function(css, template, api) {

	var ko = api.ko,
		key = api.key
		Widget = api.Widget;

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
			// TODO:  find a better way to retrieve the view
			var ul = $(views[2])
			if(!ul.find(".active").length) {
				ul.find("li,dd").first().addClass("active")
			}
			// Click helper
			// Need Bubbling here
			ul[0].addEventListener("click",function(e){
				$(e.target).closest("li,dd").addClass("active").siblings().removeClass("active");
			},false);

		}
	});
	return Browser;

})