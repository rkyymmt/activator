var templates = req('core/templates');

$.fn.getOrElse = function(selector, ctx){
	return this.length ? $(this) : $(selector, ctx)
}

function inUrl(path, url){
	url = url || window.location.hash;
	url = url.search("#") < 0 ? url.substr(1) : url;
	url = url.search("/") < 0 ? [url] : url.split('/');

	path = path.search("#") < 0 ? path.substr(1) : path;
	path = path.search("/") < 0 ? [path] : path.split('/');

	if (url.length < path.length) return false;
	else if (url.length > path.length) url.splice(path.length, -1);

	for (i in url){
		if (url[i] != path[i]) return false;
	}

	return true;
};

function noop(){};

/* Dead simple events manager
 * Usage:
 * var evented = Event({
 * 	prop: ""
 * })
 * evented.bind("evt", function(e){
 * 	console.log(this, e)
 * })
 * evented.trigger("evt", "param")
 */
var Event = function(o){
	o.bind = function(event, fct){
		this._events = this._events || {};
		this._events[event] = this._events[event]	|| [];
		this._events[event].push(fct);
	}
	o.unbind = function(event, fct){
		this._events = this._events || {};
		if( event in this._events === false  )	return;
		this._events[event].splice(this._events[event].indexOf(fct), 1);
	}
	o.trigger = function(event /* , args... */){
		this._events = this._events || {};
		if( event in this._events === false  )	return;
		for(var i = 0; i < this._events[event].length; i++){
			this._events[event][i].apply(o, Array.prototype.slice.call(arguments, 1));
		}
	}
	return o;
};


/* Local storage helper for UI settings
 */
var Settings = (function(){
	var settings = window.localStorage.settings || {};
	function set(label, value){
		window.localStorage.settings.setItem(label, JSON.stringify(value));
	}
	function get(label, def){
		return JSON.parse(window.localStorage.settings.getItem(label)) || def;
	}
	function reset(label){
		window.localStorage.settings.removeItem('label');
	}
	return {
		set: set,
		get: get,
		reset: reset
	}
}());

var Class = function(o){
	function M(){
		if (this.init){
			this.init.apply(this,[].slice.call(arguments, 0))
		}
		// proxy is an array of method names
		if (this.proxy){
			$.each(this.proxy,function(k,v){
				$.proxy(this[v], this);
			});
		}
	}
	M.extend = function(o){
		M.prototype = $.extend(o,M.prototype);
	}
	M.extend(o);
	return M
}


// base class for widgets, with convenience.
// All widget classes should support the following static fields:
//   id - The identifier of the widget.
//   template - The string value of the template, unless "view" is specified.
//   view     - THe id of the template used in the template engine.
//   init     - A function to run when constructing the widget.  Takes the breadcrumb as argument (TODO - Define).
// In addition, widgets my optionally have the following methods:
// onRender - This is called when the widget is rendered to the screen.
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
  var WidgetClass = Class(o)
  WidgetClass.extend({
    // Default onRender that does nothing.
    onRender: function(elements) {}
  });
  return WidgetClass;
}
