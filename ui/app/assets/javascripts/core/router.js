// Routes are nested:
// " /projects/:id/code/ " would call
// Projects , Project(:id) and Code objects
// from assets/apps/ folder
// String routes may also be used by those modules
// to call the server.
var Router = {

	'monitor': 				[ "todo/todo" , {
		'logs': 			[ "todo/todo" ],
		'tests': 			[ "todo/todo" ],
		'application': 		[ "todo/todo" ],
		'system': 			[ "todo/todo" ],
		'rest-console': 	[ "todo/todo" ]
	}],
	'projects': 			[ "demo/demo" , {
		':id': 				[ "todo/todo" , {
			'dashboard': 	[ "todo/todo" ],
			'tasks': 		[ "todo/todo" ],
			'code': 		[ "todo/todo" ],
			'documents': 	[ "todo/todo" ],
			'people': 		[ "todo/todo" ],
			'options': 		[ "todo/todo" ]
		}],
		'create': 			[ "todo/todo" ]
	}],
	'code': 				[ "code/code" , {
		'commits': 			[ "todo/todo" ],
		'branches': 		[ "todo/todo" ],
		'comments': 		[ "todo/todo" ],
		'wiki': 			[ "todo/todo" ],
		':id':				[ "code/browse" , {
			':id':				[ "code/browse" , {
				':id':				[ "code/browse" , {
					':id':				[ "code/browse" , {':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse",{':id':["code/browse"]}]}]}]}]}]}]}]}]}]}]}]
				}]
			}]
		}]
	}],
	':id': 					[ "todo/todo" ]

}
