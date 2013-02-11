define(["text!./viewImage.html", 'core/pluginapi'], function(template, api){

	var ImageView = api.Widget({
		id: 'code-image-view',
		template: template,
		init: function(args) {
			this.fileLoadUrl = args.fileLoadUrl;
		}
	});
	return ImageView;
});