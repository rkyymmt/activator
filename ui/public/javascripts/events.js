// TODO - We should make more robust listeners and filtering and junk.
snap_events = function() {

  var source = new EventSource("/api/event/stream");
  
  source.addEventListener('message', function(e) {
    snap_events.sendEvent(JSON.parse(e.data));
  }, false);
   
  return {
    listeners: [],
    
    // Register a listener with the event service.
    // A listener is a function that takes an event as it is received.  All filtering should happen in listeners for now.
    addListener: function(listener) {
      this.listeners.push(listener)
    },
    sendEvent: function(msg) {
      // Send the event to all registered listeners.
      $.each(this.listeners, function(idx, item) { item(msg); })
    }
  };
}();