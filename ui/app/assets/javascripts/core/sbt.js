define(['./streams', './events', './utils'], function(streams, events, utils) {

	// Internal list of subscribers for task events.
	var taskSubscribers = [];

	function taskMultiplexer(obj) {
		var oldSubscribers = taskSubscribers;

		if (obj.event.type == "TaskComplete") {
			console.log("task " + obj.taskId + " complete, removing its subscribers");
			// $.grep callback takes value,index while $.each takes index,value
			// awesome?
			taskSubscribers = $.grep(taskSubscribers, function(subscriber, index) {
				// keep only tasks that are not complete
				return subscriber.taskId != obj.taskId;
			});
		}

		// Now forward everything on to the previous (pre-removal)
		// subscriber list.
		$.each(oldSubscribers, function(index, subscriber) {
			if (subscriber.taskId == obj.taskId) {
				try {
					subscriber.handler(obj.event);
				} catch(e) {
					console.log("handler for " + subscriber.taskId + " failed", e);
				}
			}
		});
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
	var taskSubscription = streams.subscribe({
		filter: isSbtTaskEvent,
		handler: taskMultiplexer
	});

	function onFilesChanged(obj) {
		// forward to the inter-plugin event bus
		events.send(obj);
	}

	function isFilesChangedEvent(obj) {
		return ('type' in obj) && obj.type == 'FilesChanged';
	}

	var filesChangedSubscription = streams.subscribe({
		filter: isFilesChangedEvent,
		handler: onFilesChanged
	});

	function doNothing() {}
	function functionOrNothing(f, context) {
		if (typeof(f) == 'function') {
			if (typeof(context) == 'object')
				return f.bind(context);
			else
				return f;
		} else {
			return doNothing;
		}
	}

	var AjaxPromise = utils.Class({
		init: function(o, url, request) {
			if (typeof(o) != 'object')
				throw new Error("missing object parameter in AjaxPromise");
			if (typeof(url) != 'string')
				throw new Error("missing string parameter in AjaxPromise");
			if (typeof(request) != 'object')
				throw new Error("missing request body in AjaxPromise");

			this.completed = false;
			// deprecated usage
			if ('error' in o)
				throw new Error("name your error callback 'failure' not 'error' please");

			this.onSuccess = functionOrNothing(o.success, o.context);
			this.onFailure = functionOrNothing(o.failure, o.context);
			this.url = url;
			this.request = request;
		},
		fail: function(status, message) {
			if (!this.completed) {
				this.completed = true;
				this.onFailure(status, message);
			}
		},
		succeed: function(data) {
			if (!this.completed) {
				this.completed = true;
				this.onSuccess(data);
			}
		},
		_onAjaxSuccess: function(data) {
			if ('type' in data && data.type == 'ErrorResponse') {
				console.log("ajax ErrorResponse ", data);
				this.fail('error', data.error);
			} else {
				console.log("ajax success ", data);
				this.succeed(data);
			}
		},
		_onAjaxError: function(xhr, status, message) {
			console.log("ajax error ", status, message)
			this.fail(status, message);
		},
		send: function() {
			var areq = {
				url: this.url,
				type: 'POST',
				dataType: 'json', // return type
				contentType: 'application/json; charset=utf-8',
				data: JSON.stringify(this.request)
			};
			areq.success = this._onAjaxSuccess.bind(this);
			areq.error = this._onAjaxError.bind(this);

			console.log("sending ajax request ", this.request)
			return $.ajax(areq);
		}
	});

	function randomShort() {
		return Math.floor(Math.random() * 65536);
	}

	function genTaskId(prefix) {
		return prefix + "-" + (new Date().getTime()) + "-" + randomShort() + "-" + randomShort() + "-" + randomShort();
	}

	// TaskPromise can be completed by ajax failure, or by
	// getting the TaskComplete event.
	var TaskPromise = utils.Class({
		init: function(o) {
			this.completed = false;
			this.requestJson = this.buildRequestJson(o.task);
			this.onMessage = functionOrNothing(o.onmessage, o.context);
			this.onSuccess = functionOrNothing(o.success, o.context);
			this.onFailure = functionOrNothing(o.failure, o.context);

			subscribeTask(this.requestJson.taskId, this.messageHandler.bind(this));

			this.promise = new AjaxPromise({
				success: this._onAjaxSuccess.bind(this),
				failure: this._onAjaxError.bind(this)
			}, '/api/sbt/task', this.requestJson)
		},

		/** Generates the json for our task request.
		 * @param o {String|Object}
		 *          Either the task string to run, or an object with the
		 *          following format:
		 *             task - The task id to run
		 *             description - The description for this task request.
		 *             params - extra parameters for the task
		 */
		buildRequestJson: function(o) {
			var taskName = (typeof(o) == 'string') ? o : o.task;
			if (typeof(taskName) != 'string')
				throw new Error("No task name found");
			var request = {
				appId: serverAppModel.id,
				taskId: genTaskId(serverAppModel.id),
				description: (o.description  || (taskName + " " + serverAppModel.id)),
				task: {
					type: 'GenericRequest',
					name: taskName
				}
			};
			if (typeof(o.params) == 'object')
				request.task.params = o.params;
			return request;
		},
		fail: function(status, message) {
			if (!this.completed) {
				this.completed = true;
				this.onFailure(status, message);
			}
		},
		succeed: function(data) {
			if (!this.completed) {
				this.completed = true;
				this.onSuccess(data);
			}
		},
		messageHandler: function(event) {
			console.log("got event in TaskPromise ", event);
			if (event.type == 'TaskComplete') {
				if (event.response.type == 'ErrorResponse') {
					this.fail('error', event.response.error);
				} else {
					this.succeed(event.response);
				}
			} else {
				// drop all events if we're already
				// completed (should not happen really)
				if (this.completed) {
					console.log("Task already completed so dropping event", event);
				} else {
					this.onMessage(event);
				}
			}
		},
		taskId: function() {
			return this.requestJson.taskId;
		},
		_onAjaxSuccess: function(data) {
			// The ajax request completes when the request is received
			// by sbt, not when sbt finishes the request.
			// Errors happen if there's a problem getting the request
			// to sbt.
			if (data.type == 'RequestReceivedEvent') {
				// do nothing, this is expected; wait for TaskComplete event
				// to fire the success callback.
			} else {
				console.log("Unexpected ajax call result ", data);
			}
		},
		_onAjaxError: function(status, message) {
			this.fail(status, message);
		},
		send: function() {
			this.promise.send();
		}
	});

	/**
	 * Runs an SBT task, attaching listeners for in-progress information
	 * updates, or general success/failure. Returns the task ID.
	 *
	 * @param o {Object}  An object havin the following format:
	 *        - task -> The task request, either a string or object with fields task, description, params
	 *        - onmessage (optional)  ->  A handle for SBT events.
	 *        - success (optional) -> A handler for when the request is successfully delivered.
	 *        - failure (optional) -> A handler for when the request fails to be delivered.
	 *        - context (optional) -> A new 'this' object for the various callbacks.
	 */
	function runTask(o) {
		var promise = new TaskPromise(o);
		promise.send();
		return promise.taskId();
	}

	/**
	 * Kills a task by ID. Fire-and-forget (i.e. you won't know if the
	 * task never existed)
	 *
	 * @param o {Object}  An object having the following format:
	 *        - taskId -> The task ID from runTask
	 *        - success (optional) -> A handler for when the request is successfully delivered.
	 *        - failure (optional) -> A handler for when the request fails to be delivered.
	 *        - context (optional) -> A new 'this' object for the various callbacks.
	 */
	function killTask(o) {
		if (!('taskId' in o))
			throw new Error("no taskId to kill");
		var request = {
			appId: serverAppModel.id,
			taskId: o.taskId
		};

		var promise = new AjaxPromise(o, '/api/sbt/killTask', request);
		promise.send();
	}

	function watchSources(o) {
		var request = {
			appId: serverAppModel.id,
			taskId: genTaskId(serverAppModel.id)
		};

		if (typeof(o.onmessage) == 'function') {
			subscribeTask(request.taskId, functionOrNothing(o.onmessage, o.context));
		}

		var promise = new AjaxPromise(o, '/api/sbt/watchSources', request);
		promise.send();

		return request.taskId;
	}

	return {
		runTask: runTask,
		killTask: killTask,
		watchSources: watchSources
	};
});
