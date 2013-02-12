define(['./streams'], function(streams) {

	// Internal list of subscribers for task events.
	var taskSubscribers = [];

	function taskMultiplexer(obj) {
			if (obj.event.type == "TaskComplete") {
				console.log("task " + obj.taskId + " complete, removing its subscribers");
				// $.grep callback takes value,index while $.each takes index,value
				// awesome?
				taskSubscribers = $.grep(taskSubscribers, function(subscriber, index) {
					// keep only tasks that are not complete
					return subscriber.taskId != obj.taskId;
				});
			} else {
				// Just forward everything on.
			$.each(taskSubscribers, function(index, subscriber) {
				if (subscriber.taskId == obj.taskId) {
					try {
						subscriber.handler(obj.event);
					} catch(e) {
						console.log("handler for " + subscriber.taskId + " failed", e);
					}
				}
			});
			}
	}

	function isSbtTaskEvent(obj) {
		return 'taskId' in obj;
	}

	// this is probably sort of a hacky API but it will get us going.
	// May want to refactor to a more generic event bus thingy.
	function subscribeTask(taskId, handler) {
	var subscriber = { taskId: taskId, handler: handler }
	taskSubscribers.push(subscriber)
	 }

	// Subscribes our own task-event streams to the websocket, and we hide that from
	// clients.  We need to detail how events flow more formally in apis, but
	// this is used so we can deregister from the socket if needed.
	var subscription = streams.subscribe({
	filter: isSbtTaskEvent,
	handler: taskMultiplexer
	});


	function randomShort() {
		return Math.floor(Math.random() * 65536)
	}

	function genTaskId(prefix) {
		return prefix + "-" + (new Date().getTime()) + "-" + randomShort() + "-" + randomShort() + "-" + randomShort();
	}

	/** Generates an SbtTaskRequest.  This attempts a series of input types into
	 * the only acceptable JSON for the server.
	 * @param o {String|Object}
	 *          Either the task string to run, or an object with the
	 *          following format:
	 *             task - The task id to run
	 *             description - The description for this task request.
	 */
	function SbtTaskRequest(o) {
		var taskName = (typeof(o) == 'string') ? o : o.task;
		var request = {
			appId: serverAppModel.id,
			taskId: genTaskId(serverAppModel.id),
			description: (o.description  || (taskName + " " + serverAppModel.id)),
			task: {
				type: taskName
			}
		};
		return request;
	};

	/**
	 * Runs an SBT task, attaching listeners for in-progress information
	 * updates, or general success/failure.
	 *
	 * @param o {Object}  An object havin the following format:
	 *        - task -> The task request (anything acceptible to the SbtTaskRequest is
	 *                  accepted here.
	 *        - onmessage (optional)  ->  A handle for SBT events.
	 *        - success (optional) -> A handler for when the request is successfully delivered.
	 *        - failure (optional) -> A handler for when the request fails to be delivered.
	 *        - context (optional) -> A new 'this' object for the various callbacks.
	 */
	function runTask(o) {
		var request = SbtTaskRequest(o.task);

		// TODO - Ensure sane-ness of data...

		// Register our listener appropriately.
		if(o.onmessage) {
			var handler = o.onmessage;
			// Update handler to have correct 'this' if necessary.
			if(o.context) {
				var tmp = o.onmessage;
				var scope = o.context;
				handler = function() {
					tmp.call(scope, [].slice.call(arguments, 0))
				}
			}
			subscribeTask(request.taskId, handler);
		}

		// Make SBT AJAX call now.
		var areq = {
			url: '/api/sbt/task',
			type: 'POST',
			dataType: 'json', // return type
			contentType: 'application/json; charset=utf-8',
			data: JSON.stringify(request)
		};
		if(o.context) areq.context = o.context;
		if(o.failure) areq.failure = o.failure;
		if(o.success) areq.success = o.success;
		$.ajax(areq);
	}

	return {
		runTask: runTask
	};
});
