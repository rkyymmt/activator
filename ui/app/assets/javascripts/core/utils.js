define([], function() {

$.fn.getOrElse = function(selector, ctx) {
	return this.length ? $(this) : $(selector, ctx)
}

var Class = function(o) {
	function M() {
		if(this.init) {
			this.init.apply(this, [].slice.call(arguments, 0))
		}
		// proxy is an array of method names
		if(this.proxy) {
			$.each(this.proxy, function(k, v) {
				$.proxy(this[v], this);
			});
		}
	}
	M.extend = function(o) {
		M.prototype = $.extend(o, M.prototype);
	}
	M.extend(o);
	return M
}


	return {
		Class: Class
	}
});
