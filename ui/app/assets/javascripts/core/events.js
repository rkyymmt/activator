// event bus allowing plugins to communicate with each other
define(function() {

	var subscribers = [];
	var nextId = 0;

	function subscribe(filter, handler) {
		var subscriber = { id: nextId, filter: filter, handler: handler };
		nextId = nextId + 1;
		subscribers.push(subscriber);
		return subscriber.id;
	}

	function unsubscribe(id) {
		subscribers = $.grep(subscribers, function(subscriber, index) {
			return subscriber.id !== id;
		});
	}

	function send(event) {
		if (!('type' in event))
			throw new Error("event to send must have a 'type' field");
		console.log("sending event ", event)
		$.each(subscribers, function(index, subscriber) {
			try {
				if (subscriber.filter(event))
					subscriber.handler(event);
			} catch(e) {
				console.log("filter or handler failed", e);
			}
		});
	}

	return {
		subscribe: subscribe,
		unsubscribe: unsubscribe,
		send: send
	};
});
