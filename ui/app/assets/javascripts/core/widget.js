define(['vendors/knockout-2.2.1.debug','./utils', './templates'], function(ko, utils, templates) {

// base class for widgets, with convenience.
//All widget classes should support the following static fields:
//  id - The identifier of the widget.
//  template - The string value of the template, unless "view" is specified.
//  view     - The id of the template used in the template engine.
//  init     - A function to run when constructing the widget, can have whatever args you want
//In addition, widgets my optionally have the following methods:
//onRender - This is called when the widget is rendered to the screen.
var Widget = utils.Class({
	// one-time class constructor
	classInit: function(proto) {
		// we can't sanity-check that we have either template/id or view,
		// here, because we may be an abstract class. We check in init()
		// since only concrete classes can be constructed.

		// register the template to create a view, given id and template string
		if (proto.template && proto.id && !proto.view) {
			proto.view = templates.registerTemplate(proto.id, proto.template);
		}
	},
	init: function() {
		if (!this.view) {
			console.error("defective widget has no view ... no template/id field? attempt to instantiate abstract class?", this);
			throw new Error("Widget classes must provide either ('id' and 'template') or 'view' field");
		}
	},
	// Default onRender that does nothing.
	onRender: function(elements) {},
	renderTo: function(el) {
		// Here we're binding ourselves directly to an element, not using
		// the normal knockout "bind everything" magic...
		var element = $(el);
		element.attr('data-bind', 'snapView: $data');
		ko.applyBindings(this, element.get()[0]);
	}
});

// Copied from knockout source..
var REPLACE_CHILDREN_TEMPLATE_MODE = 'replaceChildren';
var REPLACE_ELEMENT_TEMPLATE_MODE = 'replaceNode;'

// This little beauty gives us the ability to not have to specify all the knockout render
// template parameters.
ko.bindingHandlers.snapView = {
		init: function(element, valueAccessor) {
			return { 'controlsDescendantBindings': true };
		},
		update: function(element, valueAccessor) {
			// TODO - figure out if we need to unwrap, rather than trust we don't
			var widget = valueAccessor();
			// TODO - Find a way to load in replace children vs. replace element mode, and
			// get rid of snapViewReplace binding.
			var opts = {
					afterRender: widget.onRender.bind(widget)
			}
			ko.renderTemplate(widget.view, widget, opts, element, REPLACE_CHILDREN_TEMPLATE_MODE);
		}
};

// This guy has the template replace the element, rather than embedd inside the children.
ko.bindingHandlers.snapViewReplace = {
		init: function(element, valueAccessor) {
			return { 'controlsDescendantBindings': true };
		},
		update: function(element, valueAccessor) {
			// TODO - figure out if we need to unwrap...
			var widget = valueAccessor();
			var opts = {
					data: widget,
					afterRender: widget.onRender.bind(widget)
			}
			ko.renderTemplate(widget.view, widget, opts, element, REPLACE_ELEMENT_TEMPLATE_MODE);
		}
}

return Widget;
});
