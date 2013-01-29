define(["text!./viewImage.html"], function(template){

	var ImageView = Widget({
		id: 'code-image-view',
		template: template,
		init: function(args) {
			this.fileLoadUrl = args.fileLoadUrl;
		}
	});
	return ImageView;
});