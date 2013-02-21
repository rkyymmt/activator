define(['core/pluginapi', 'text!./home.html', './files', './browse', 'css!./code.css'], function(api, template, files, Browser){

	var ko = api.ko;

	var CodeCore = api.Widget({
		id: 'code-core',
		template: template,
		init: function() {
			var self = this;
			self.currentDirectory = ko.observable(new files.FileModel({
				location: serverAppModel.location
			}));
			self.currentFile = ko.observable(self.currentDirectory());
			self.status = ko.observable('');
			self.browser = new Browser({
				directory: self.currentDirectory
			});
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
				var file = serverAppModel.location + '/' + bcs.rest.join('/');
				if(home.currentFile().location != file) {
					home.currentFile(new files.FileModel({
						location: file,
						autoLoad: true
					}));
				}
				var dir = serverAppModel.location + '/' + bcs.rest.slice(0, bcs.rest.length - 1).join('/')
				if(home.currentDirectory().location != dir) {
					home.currentDirectory(new files.FileModel({
						location: dir,
						autoLoad: true
					}));
				}
				// TODO - Do we need to load the file info?
			}
		},
		widgets: [home]
	});

});
