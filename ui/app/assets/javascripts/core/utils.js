define([], function() {

$.fn.getOrElse = function(selector, ctx) {
	return this.length ? $(this) : $(selector, ctx)
}

function inUrl(path, url) {
	url = url || window.location.hash;
	url = url.search("#") < 0 ? url.substr(1) : url;
	url = url.search("/") < 0 ? [url] : url.split('/');

	path = path.search("#") < 0 ? path.substr(1) : path;
	path = path.search("/") < 0 ? [path] : path.split('/');

	if(url.length < path.length) return false;
	else if(url.length > path.length) url.splice(path.length, -1);

	for(i in url) {
		if(url[i] != path[i]) return false;
	}

	return true;
};

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
		Class: Class,
		inUrl: inUrl
	}
});
