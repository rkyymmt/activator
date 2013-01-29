define(["text!./viewCode.html"], function(template){

	var ko = req('vendors/knockout-2.2.1.debug');

	// Fetch utility
	function show(location){
		return $.ajax({
			url: '/api/local/show',
			type: 'GET',
			dataType: 'text',
			data: { location: location }
		});
	}

	var ImageView = Widget({
		id: 'code-edit-view',
		template: template,
		init: function(args) {
			this.fileLoc = args.fileLoc;
			this.fileLoadUrl = args.fileLoadUrl;
			this.contents = ko.observable('Loading...');
			this.load();
		},
		load: function() {
			var self = this;
			show(self.fileLoc).done(function(contents) {
				self.contents(contents);
			});
		}
	});
	return ImageView;
});