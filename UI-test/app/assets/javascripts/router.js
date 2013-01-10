// Routes are nested:
// " /projects/:id/code/ " would call
// Projects , Project(id) and Code objects
// from assets/apps/ folder
// String routes may also be used by those modules
// to call the server.
var Router = {

	'dashboard': 			[ Dashboard ]
  , 'messaging': 			[ Todo ]
  , 'agenda': 				[ Todo ]
  , 'documents': 			[ Todo , {
		// Too lazy, #weird
		':id': 				[ Todo , { ':id': [ Todo , { ':id': [ Todo , { ':id': [ Todo ] }] }] }]
	}]
  , 'monitor': 				[ Play , {
		//':id': 					[ Play , {
			'logs': 			[ Logs ]
		  , 'tests': 			[ Tests ]
		  , 'application': 		[ AppMonitor ]
		  , 'system': 			[ SysMonitor ]
		  , 'rest-console': 	[ RestConsole ]
		//}]
	}]
  , 'projects': 			[ Projects , {
		':id': 				[ Project , {
			'dashboard': 	[ Todo ]
		  , 'tasks': 		[ Tasks ]
		  , 'code': 		[ Todo ]
		  , 'documents': 	[ Todo ]
		  , 'people': 		[ Todo ]
		  , 'options': 		[ Todo ]
		}]
	  , 'create': 			[ Todo ]
	}]
  , 'people': 				[ Todo , {
		':id': 				[ Todo , {
			':id': 			[ Todo ]
		  , 'edit': 		[ Todo ]
		}]
	  , 'create': 			[ Todo ]
	}]
  , 'clients': 				[ Todo , {
		':id': 				[ Todo , {
			'dashboard': 	[ Todo ]
		  , 'code': 		[ Todo ]
		  , 'documents': 	[ Todo ]
		  , 'people': 		[ Todo ]
		}]
	  , 'create': 			[ Todo ]
	}]
  , ':id': 					[ Todo ]
}
