var Todo = {
		name: "TODO"
	  , tpl: _.template( $('#todo_tpl').text() )
	  , init: function(){
		}
}

function inUrl(path, url){
	url = url || window.location.hash
	url = url.search("#") < 0 ? url.substr(1) : url
	url = url.search("/") < 0 ? [url] : url.split('/')

	path = path.search("#") < 0 ? path.substr(1) : path
	path = path.search("/") < 0 ? [path] : path.split('/')

	if (url.length < path.length) return false
	else if (url.length > path.length) url.splice(path.length, -1)

	for (i in url){
		if (url[i] != path[i]) return false
	}

	return true
}
