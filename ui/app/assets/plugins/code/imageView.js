define(["text!./viewImage.html", 'core/pluginapi'], function(template, api){

	var ImageView = api.Widget({
		id: 'code-image-view',
		template: template,
		init: function(args) {
			var self = this;
			self.file = args.file;
			self.fileLoadUrl = api.ko.computed(function() {
				var file = self.file();
				return '/api/local/show?location=' + file.location;
			});
		}
	});
	return ImageView;
});
