define(['vendors/knockout-2.2.1.debug','./utils', './templates'], function(ko, utils, templates) {

//base class for widgets, with convenience.
//All widget classes should support the following static fields:
//  id - The identifier of the widget.
//  template - The string value of the template, unless "view" is specified.
//  view     - THe id of the template used in the template engine.
//  init     - A function to run when constructing the widget.  Takes the breadcrumb as argument (TODO - Define).
//In addition, widgets my optionally have the following methods:
//onRender - This is called when the widget is rendered to the screen.
var Widget = function(o) {
	//var oldinit = o.init;
	// Set up templating stuff
	if(o.template && o.id && !o.view) {
		o.view = templates.registerTemplate(o.id, o.template);
	}
	// TODO - Throw error if template + id *or* view are not defined...
	// TODO - Tell user why templating stuff has to be done *statically*, not
	//        on a per-instance basis.
	/*o.init = function(args) {
		// Now call user's init method.
		if(oldinit) { oldinit.call(this, args); }
		}*/
	var WidgetClass = utils.Class(o)
	WidgetClass.extend({
		// Default onRender that does nothing.
		onRender: function(elements) {},
		renderTo: function(el) {
			// Here we're binding ourselves directly to an element, not using
			// the normal knockout "bind everything" magic...
			var element = $(el);
			element.attr('data-bind', 'template: { name: \''+this.view+'\', data: $data, afterRender: onRender }');
			ko.applyBindings(this, element.get()[0]);
		}
	});
	return WidgetClass;
};

return Widget;
});
