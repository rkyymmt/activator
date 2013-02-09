define(function() {
    var ko = req('vendors/knockout-2.2.1.debug');
    var id = window.serverAppModel.wsUrl;
    // We can probably just connect immediately.
    var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket;

    console.log("WS opening: " + window.serverAppModel.wsUrl);
    var socket = new WS(window.serverAppModel.wsUrl);

    var taskSubscribers = []

    function sendMessage(msg) {
      socket.send(JSON.stringify(msg));
    }

    // this is probably sort of a hacky API but it will get us going.
    // May want to refactor to a more generic event bus thingy.
    function subscribeTask(taskId, handler) {
    	var subscriber = { taskId: taskId, handler: handler }
    	taskSubscribers.push(subscriber)
    }

    function receiveEvent(event) {
    	console.log("WS Event: ", event.data, event);
    	var obj = JSON.parse(event.data);
    	if ('taskId' in obj) {
    		if (obj.event.type == "TaskComplete") {
    			console.log("task " + obj.taskId + " complete, removing its subscribers");
    			// $.grep callback takes value,index while $.each takes index,value
    			// awesome?
    			taskSubscribers = $.grep(taskSubscribers, function(subscriber, index) {
    				// keep only tasks that are not complete
    				return subscriber.taskId != obj.taskId;
    			});
    		} else {
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
    }

    function onOpen(event) {
    	console.log("WS opened: ", event)
    }

    function onClose(event) {
    	console.log("WS closed: " + event.code + ": " + event.reason, event)
    	// TODO we might basically shut down the app here and give the user
    	// a reload button (we could auto-reload but might get in an infinite
    	// loop that way).
    }

    function onError(event) {
    	console.log("WS error: ", event)
        // TODO do same as for closed?
    }

    socket.onopen = onOpen;
    socket.onmessage = receiveEvent;
    socket.onclose = onClose;
    socket.onerror = onError;

    // TODO - Create global event stream or some such so we can add listeners and fire events.

    return {
    	// TODO - we need more public API then just "send message".
    	send: sendMessage,
    	subscribeTask: subscribeTask
    };
});
