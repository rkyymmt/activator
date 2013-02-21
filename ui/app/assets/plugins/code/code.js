define(['core/pluginapi', 'text!./home.html', './files', './browse', './view', 'css!./code.css'], function(api, template, files, Browser, Viewer){

	var ko = api.ko;

	var CodeCore = api.Widget({
		id: 'code-core',
		template: template,
		init: function() {
			var self = this;
			self.relativeCrumbs = ko.observableArray([]);
			self.root = new files.FileModel({
				location: serverAppModel.location,
				autoLoad: true
			});
			self.currentDirectory = ko.computed(function() {
				var dir = self.root;
				var crumbs = self.relativeCrumbs();
				for(var idx = 0; idx < crumbs.length; idx++) {
					var files = dir.childLookup();
					var crumb = crumbs[idx];
					if(files.hasOwnProperty(crumb) && files[crumb].loadInfo().isDirectory()) {
						dir = files[crumb];
					} else {
						return dir;
					}
				}
				return dir;
			});
			self.currentFile = ko.computed(function() {
				var file= self.root;
				var crumbs = self.relativeCrumbs();
				for(var idx = 0; idx < crumbs.length && file.isDirectory(); idx++) {
					var files = file.childLookup();
					var crumb = crumbs[idx];
					if(files.hasOwnProperty(crumb)) {
						file = files[crumb];
					} else {
						return file;
					}
				}
				return file;
			});
			self.status = ko.observable('');
			self.browser = new Browser({
				directory: self.currentDirectory
			});
			self.viewer = new Viewer({
				file: self.currentFile
			})
		}
	});

	var home = new CodeCore();

	return api.Plugin({
		id: 'code',
		name: "Code",
		icon: "",
		url: "#code",
		routes: {
			code: function(bcs) {
				// Make us the default widget, and try to find the current file.
				api.setActiveWidget(home);
				home.relativeCrumbs(bcs.rest);
			}
		},
		widgets: [home]
	});

});
