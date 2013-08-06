define(['css!./overlay.css', 'text!./overlay.html', 'webjars!knockout', 'core/widget', 'core/utils'], function(css, template, ko, Widget, utils){

	var Overlay = utils.Class(Widget, {
		id: 'overlay-widget',
		template: template,
		init: function(parameters) {
			this.contents = parameters.contents;
			this.node = null; // filled in on render
			this.css = parameters.css || '';
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
			if (this.node === null) {
				// Create a generic node for us to render to...
				var node = $('<div class="' + this.css + ' hidden"></div>').get()[0];
				$(document.body).append(node);
				this.renderTo(node);
				this.node = $(node);
			}
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
