define(['css!./overlay.css', 'text!./overlay.html', 'vendors/knockout-2.2.1.debug', 'core/widget'], function(css, template, ko, Widget){

	var Overlay = Widget({
		id: 'overlay-widget',
		template: template,
		init: function(parameters) {
			this.contentView = parameters.contentView;
			this.contentModel = parameters.contentModel;
			this.node = null; // filled in on render
		},
		onRender: function(childElements) {
			if (this.id != 'overlay-widget')
				throw new Error("wrong this in onRender " + this);
			if (this.node !== null)
				throw new Error("rendering Overlay twice");

			this.node = $(childElements[0]).parent();
			if (!this.node)
				throw new Error("didn't get the overlay node");
		},
		_checkNode: function() {
			if (this.node === null)
				console.error("right now to use overlay you need boilerplate to set up onRender")
		},
		close: function() {
			this._checkNode();
			this.node.fadeOut();
		},
		open: function() {
			this._checkNode();
			this.node.fadeIn();
		}
	});

	return Overlay;
});
